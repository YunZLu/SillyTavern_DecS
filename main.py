# 流式接收目标服务器的响应并逐步返回给客户端
@app.route("/<path:target>", methods=["POST"])
async def capture_and_forward(target):
    try:
        data = await request.get_json()
        messages = data.get("messages")
        client_ip = request.headers.get("X-Forwarded-For", request.remote_addr)
        target_url = resolve_target_url(target)

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
            async with httpx.AsyncClient() as client:
                async with client.stream("POST", target_url, json={"messages": messages}) as response:
                    async def generate():
                        async for chunk in response.aiter_bytes(chunk_size=4096):
                            yield chunk

                    # 直接返回生成器，不需要 stream_with_context
                    return Response(generate(), content_type="application/octet-stream")

    except Exception as e:
        logging.error(f"处理请求时发生错误: {e}")
        return jsonify({"error": "内部错误"}), 500
