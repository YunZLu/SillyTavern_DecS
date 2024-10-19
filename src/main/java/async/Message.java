package async;

public class Message {
    private String role;  // 新增 role 字段
    private String content;  // 内容字段，存储需要解密或处理的消息内容

    // 构造函数
    public Message(String role, String content) {
        this.role = role;
        this.content = content;
    }

    // Getter 和 Setter 方法
    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

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
                "role='" + role + '\'' +
                ", content='" + content + '\'' +
                '}';
    }
}
