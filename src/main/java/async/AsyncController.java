@PostMapping("/**")
public Flux<DataBuffer> captureAndForward(@RequestBody RequestBodyData requestBodyData,
                                          @RequestHeader HttpHeaders headers,
                                          @RequestHeader(value = "X-Forwarded-For", defaultValue = "localhost") String clientIp,
                                          ServerHttpRequest request) {
    // 从请求中提取完整的 URI，并去掉前缀 "/"
    String path = request.getPath().pathWithinApplication().value();
    String targetUrl = path.substring(1);  // 去掉前面的 "/"
    
    // Log the original request URL
    logger.info("接收到请求，完整路径: {}", path);
    logger.info("解析出的目标URL: {}", targetUrl);
    
    // 检查是否是合法的 URL
    if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
        logger.error("无效的目标URL: {}", targetUrl);
        return Flux.error(new IllegalArgumentException("Invalid target URL"));
    }

    if (requestBodyData.getMessages() == null || requestBodyData.getMessages().isEmpty()) {
        logger.error("请求体中没有消息需要处理");
        return Flux.error(new IllegalArgumentException("没有消息需要处理"));
    }

    AtomicInteger currentRequests = ipRequestCount.computeIfAbsent(clientIp, k -> new AtomicInteger(0));
    if (currentRequests.incrementAndGet() > maxIPConcurrentRequests) {
        currentRequests.decrementAndGet();
        logger.warn("来自IP {} 的并发请求超过限制: {}", clientIp, maxIPConcurrentRequests);
        return Flux.error(new IllegalArgumentException("来自该IP的并发请求过多"));
    }

    // 转发请求到解析出的目标 URL
    HttpHeaders filteredHeaders = filterHeaders(headers);
    logger.info("转发到目标URL: {}", targetUrl);
    logger.debug("转发的请求数据: {}", requestBodyData);

    return Flux.fromIterable(requestBodyData.getMessages())
        .flatMap(message -> {
            try {
                if (isEncrypted(message.getContent())) {
                    String decryptedContent = decryptAndCache(message.getContent());
                    message.setContent(decryptedContent);
                    logger.debug("解密后的消息内容: {}", decryptedContent);
                }
                return Mono.just(message);
            } catch (Exception e) {
                logger.error("解密消息时发生错误: {}", e.getMessage());
                return Mono.error(e);
            }
        })
        .doOnNext(message -> logger.info("处理消息: {}", message))
        .thenMany(webClient.post()
            .uri(URI.create(targetUrl))  // 使用目标 URL 发送请求
            .headers(httpHeaders -> httpHeaders.addAll(filteredHeaders))
            .bodyValue(requestBodyData)
            .retrieve()
            .doOnSuccess(response -> logger.info("成功转发到目标URL并获得响应"))
            .bodyToFlux(DataBuffer.class))
        .doFinally(signalType -> {
            currentRequests.decrementAndGet();
            logger.info("请求完成，当前IP的请求数减少，IP: {}, 当前请求数: {}", clientIp, currentRequests.get());
        });
}
