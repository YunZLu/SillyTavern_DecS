import os
import json
import logging
import base64
import hashlib
import asyncio
from quart import Quart, request, jsonify, Response
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
ip_semaphores = defaultdict(lambda: asyncio.BoundedSemaphore(max_ip_concurrent_requests))
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
            asyncio.create_task(load_config())  # 异步加载配置

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
        for ip, semaphore in ip_semaphores.items():
            semaphore._value = max_ip_concurrent_requests  # 更新并发请求限制
            logging.info(f"IP: {ip}, 信号量设置为: {max_ip_concurrent_requests}")

        logging.info("配置加载完成:")
        logging.info(f"私钥状态: {'已加载' if private_key else '未加载'}")
        logging.info(f"白名单: {whitelist}")
        logging.info(f"最大同IP并发请求数: {max_ip_concurrent_requests}")

    except Exception as e:
        logging.error(f"加载配置文件时发生错误: {e}")
        use_default_config()

# 使用默认配置
def use_default_config():
    global private_key, whitelist, max_ip_concurrent_requests
    private_key = None
    whitelist = []
    max_ip_concurrent_requests = 2
    logging.info("使用默认配置：")
    logging.info("私钥状态: 未加载")
    logging.info("白名单: []")
    logging.info("最大同IP并发请求数: 2")

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

# 转发流程控制
@app.route("/<path:target>", methods=["POST"])
async def capture_and_forward(target):
    try:
        # 获取客户端请求的 JSON 数据（包括 messages 和其他参数）
        data = await request.get_json()
        client_ip = request.headers.get("X-Forwarded-For", request.remote_addr)
        target_url = resolve_target_url(target)

        # 检查目标 URL 是否在白名单中
        if not any(allowed_url in target_url for allowed_url in whitelist):
            logging.error(f"目标 URL 不在白名单中: {target_url}")
            return jsonify({"error": "目标服务器不在白名单中"}), 403

        # 记录收到的客户端请求信息
        logging.info(f"收到的客户端请求信息：")
        logging.info(f"客户端 IP: {client_ip}, 时间: {datetime.now()}")
        logging.info(f"客户端请求头: {dict(request.headers)}")
        logging.info(f"客户端请求体: {data}")

        if 'messages' not in data:
            return jsonify({"error": "没有消息需要处理"}), 400

        # 获取当前IP的信号量，控制并发请求数
        semaphore = ip_semaphores[client_ip]

        # 获取信号量控制并发请求，最多等待1秒
        try:
            await asyncio.wait_for(semaphore.acquire(), timeout=1)
        except asyncio.TimeoutError:
            logging.error(f"IP {client_ip} 的并发请求超出限制")
            return jsonify({"error": "并发请求数超出限制，请稍后重试"}), 429

        try:
            # 异步解密消息，保持顺序
            tasks = [
                decrypt_message_async(msg["content"]) if is_encrypted(msg["content"]) else asyncio.to_thread(lambda x: x, msg["content"])
                for msg in data["messages"]
            ]

            decrypted_contents = await asyncio.gather(*tasks)

            # 替换消息内容为解密后的内容
            for i, message in enumerate(data["messages"]):
                message["content"] = decrypted_contents[i]

            # 设置请求头，只移除 'Content-Length'
            headers = {k: v for k, v in request.headers.items() if k not in ['Content-Length', 'Host']}

            # 记录转发到目标服务器的请求信息
            logging.info(f"转发的请求信息：")
            logging.info(f"转发目标 URL: {target_url}")
            logging.info(f"转发请求头: {headers}")
            logging.info(f"转发请求体: {data}")

            # 异步发送完整的请求体到目标服务器
            async with httpx.AsyncClient(timeout=60.0) as client:
                try:
                    response = await client.post(target_url, json=data, headers=headers)  # 改用post而不是stream
                    response.raise_for_status()  # 确保抛出异常时处理错误
                except httpx.HTTPStatusError as exc:
                    error_details = exc.response.text
                    logging.error(f"目标服务器返回错误状态码: {exc.response.status_code}, 错误信息: {error_details}")
                    return jsonify({"error": "目标服务器错误"}), exc.response.status_code

                # 返回目标服务器的完整响应
                return Response(response.content, content_type="application/json")

        finally:
            semaphore.release()  # 确保处理完请求后释放信号量
            logging.info(f"IP {client_ip} 的信号量已释放")

    except Exception as e:
        logging.error(f"处理请求时发生错误: {e}")
        return jsonify({"error": "内部错误"}), 500


# 在 Quart 应用启动前加载配置
@app.before_serving
async def startup_load_config():
    await load_config()

# 主函数，设置 Watchdog 监控配置文件并启动 Quart
if __name__ == "__main__":
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
