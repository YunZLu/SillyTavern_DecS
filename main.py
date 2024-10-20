import os
import json
import logging
import base64
import hashlib
import asyncio
from quart import Quart, request, jsonify, Response, stream_with_context
import httpx
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import load_pem_private_key
from collections import defaultdict
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime

# 初始化日志配置
logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s: %(message)s')

# 初始化 Quart 应用
app = Quart(__name__)

# 全局配置变量
private_key = None
whitelist = []
max_ip_concurrent_requests = 2
ip_semaphores = defaultdict(lambda: asyncio.Semaphore(max_ip_concurrent_requests))
cache = {}

# 配置文件路径
CONFIG_PATH = "config.json"

# 动态创建线程池，用于异步解密操作，线程数基于 CPU 核心数量
executor = ThreadPoolExecutor(max_workers=os.cpu_count() * 2)

# 监控配置文件的 Handler
class ConfigFileHandler(FileSystemEventHandler):
    def on_modified(self, event):
        if event.src_path.endswith(CONFIG_PATH):
            logging.info(f"检测到 {CONFIG_PATH} 文件更新，重新加载配置")
            asyncio.run(load_config())

# 加载私钥
def load_private_key(private_key_string):
    try:
        private_key_bytes = base64.b64decode(private_key_string.encode())
        return load_pem_private_key(private_key_bytes, password=None)
    except Exception as e:
        logging.error(f"加载私钥时发生错误: {e}")
        return None

# 重新加载配置文件
async def load_config():
    global private_key, whitelist, max_ip_concurrent_requests

    if not os.path.exists(CONFIG_PATH):
        logging.warning(f"配置文件 {CONFIG_PATH} 未找到，将使用默认配置")
        use_default_config()
        return

    try:
        with open(CONFIG_PATH, 'r') as config_file:
            config = json.load(config_file)

        private_key_string = config.get("privateKey", "")
        if private_key_string:
            private_key = load_private_key(private_key_string)
        else:
            private_key = None
            logging.warning("配置文件中未找到私钥，将无法进行解密操作")

        whitelist = config.get("whitelist", [])
        max_ip_concurrent_requests = config.get("maxConcurrentRequestsPerIP", 2)

        # 动态更新每个IP的信号量
        for semaphore in ip_semaphores.values():
            semaphore._value = max_ip_concurrent_requests  # 更新并发请求限制

        logging.info(f"配置加载完成：白名单: {whitelist}, 最大同IP并发请求数: {max_ip_concurrent_requests}")

    except Exception as e:
        logging.error(f"加载配置文件时发生错误: {e}")
        use_default_config()

# 使用默认配置
def use_default_config():
    global private_key, whitelist, max_ip_concurrent_requests
    private_key = None
    whitelist = []
    max_ip_concurrent_requests = 2
    logging.info("使用默认配置：白名单为空，私钥为空，最大同IP并发请求数为2")

# 生成加密哈希
def generate_hash(message):
    return hashlib.sha256(message.encode()).hexdigest()

# 判断内容是否加密
def is_encrypted(content):
    return content.startswith("ENC:")

# 异步解密消息，并缓存
async def decrypt_message_async(encrypted_message):
    hash_key = generate_hash(encrypted_message)
    
    # 优先从缓存获取解密内容
    if hash_key in cache:
        return cache[hash_key]

    try:
        encrypted_data = base64.b64decode(encrypted_message[4:])  # 移除前缀
        loop = asyncio.get_event_loop()

        # 使用线程池进行解密操作，避免阻塞
        decrypted_data = await loop.run_in_executor(
            executor, private_key.decrypt, encrypted_data, padding.OAEP(
                mgf=padding.MGF1(algorithm=hashes.SHA256()), algorithm=hashes.SHA256(), label=None
            )
        )

        decrypted_message = decrypted_data.decode()
        cache[hash_key] = decrypted_message  # 将解密结果缓存
        return decrypted_message
    except Exception as e:
        logging.error(f"解密消息时发生错误: {e}")
        return encrypted_message  # 解密失败时返回原始消息

# 解析目标 URL
def resolve_target_url(url_or_param):
    if url_or_param.startswith("http://") or url_or_param.startswith("https://"):
        return url_or_param
    if url_or_param.startswith("url:"):
        url_or_param = url_or_param[4:]

    # 替换为你的具体URL
    if url_or_param.lower() == "claude":
        return "https://example-claude-url.com"
    elif url_or_param.lower() == "clewd":
        return "https://example-clewd-url.com"
    return "https://" + url_or_param

# 过滤请求头
def filter_headers(headers):
    excluded_headers = ["Host", "Content-Length", "Accept-Encoding", "Connection"]
    return {key: value for key, value in headers.items() if key not in excluded_headers}

# 流式接收目标服务器的响应并逐步返回给客户端
@app.route("/<path:target>", methods=["POST"])
async def capture_and_forward(target):
    try:
        # 获取客户端请求的 JSON 数据
        data = await request.get_json()
        messages = data.get("messages")
        client_ip = request.headers.get("X-Forwarded-For", request.remote_addr)
        target_url = resolve_target_url(target)

        # 日志记录请求信息
        logging.info(f"客户端 IP: {client_ip}")
        logging.info(f"转发请求目标 URL: {target_url}")
        logging.info(f"请求头: {dict(request.headers)}")
        logging.info(f"请求体: {data}")

        if not messages:
            return jsonify({"error": "没有消息需要处理"}), 400

        # 获取当前IP的信号量，控制并发请求数
        semaphore = ip_semaphores[client_ip]

        async with semaphore:
            logging.info(f"IP {client_ip} 正在处理请求")

            # 异步解密消息，保持顺序
            tasks = [
                decrypt_message_async(msg["content"]) if is_encrypted(msg["content"]) else msg["content"]
                for msg in messages
            ]

            decrypted_contents = await asyncio.gather(
                *(task if asyncio.iscoroutine(task) else asyncio.to_thread(lambda x: x, task) for task in tasks)
            )

            for i, message in enumerate(messages):
                message["content"] = decrypted_contents[i]

            # 异步发送请求到目标服务器并处理响应
            async with httpx.AsyncClient(timeout=60.0) as client:  # 设置超时为60秒
                logging.info(f"发送到目标服务器的消息: {messages}")
                async with client.stream("POST", target_url, json={"messages": messages}) as response:
                    if response.status_code != 200:
                        logging.error(f"目标服务器返回错误状态码: {response.status_code}")
                        return jsonify({"error": "目标服务器错误"}), response.status_code

                    async def generate():
                        async for chunk in response.aiter_bytes(chunk_size=4096):
                            yield chunk

                    return Response(generate(), content_type="application/octet-stream")

    except Exception as e:
        logging.error(f"处理请求时发生错误: {e}")
        return jsonify({"error": "内部错误"}), 500


# 主函数，设置 Watchdog 监控配置文件并启动 Quart
if __name__ == "__main__":
    # 异步加载配置
    asyncio.run(load_config())

    # 启动 Watchdog 监控配置文件变化
    observer = Observer()
    event_handler = ConfigFileHandler()
    observer.schedule(event_handler, path=".", recursive=False)  # 监控项目目录
    observer.start()

    try:
        app.run(host="0.0.0.0", port=5050)
    finally:
        observer.stop()
        observer.join()
