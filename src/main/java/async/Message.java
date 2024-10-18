package async;

public class Message {
    private String content;  // 内容字段，存储需要解密或处理的消息内容

    // 构造函数
    public Message(String content) {
        this.content = content;
    }

    // Getter 和 Setter 方法
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    // 增加 toString() 方法便于日志输出
    @Override
    public String toString() {
        return "Message{" +
                "content='" + content + '\'' +
                '}';
    }
}
