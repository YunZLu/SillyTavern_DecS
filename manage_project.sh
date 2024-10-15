#!/bin/bash

# 更新后的 GitHub 仓库地址和项目名
GITHUB_REPO_URL="https://github.com/YunZLu/SillyTavern_DecS.git"
PROJECT_NAME="SillyTavern_DecS"
APP_NAME="sillytavern-decs-app"
SERVICE_FILE="/etc/systemd/system/$APP_NAME.service"
SECURITY_CONFIG_FILE="src/main/java/async/SecurityConfig.java"
KEYSTORE_FILE="src/main/resources/keystore.jks"
APPLICATION_PROPERTIES_FILE="src/main/resources/application.properties"

GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 显示标题
function show_header() {
    echo -e "${BLUE}╔═══════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║       ${GREEN}欢迎使用酒馆解密服务脚本${NC}${BLUE}                     ║${NC}"
    echo -e "${BLUE}╚═══════════════════════════════════════════════════════╝${NC}"
}

# 更新系统包
function update_system() {
    echo -e "${YELLOW}>>> 更新系统包...${NC}"
    sudo apt-get update
}

# 安装依赖
function install_dependencies() {
    echo -e "${YELLOW}>>> 安装依赖: Git, OpenJDK 11, Maven, curl...${NC}"
    sudo apt-get install -y git openjdk-11-jdk maven curl
}

# 验证安装
function check_installation() {
    echo -e "${YELLOW}>>> 验证 Java 和 Maven 安装...${NC}"
    java -version
    mvn -version
}

# 自动获取服务器公网 IP 地址
function get_public_ip() {
    echo -e "${YELLOW}>>> 正在获取服务器公网 IP...${NC}"
    PUBLIC_IP=$(curl -s http://checkip.amazonaws.com)
    if [ -z "$PUBLIC_IP" ]; then
        echo -e "${RED}>>> 无法获取服务器公网 IP。请检查网络连接。${NC}"
        exit 1
    else
        echo -e "${GREEN}>>> 服务器公网 IP: $PUBLIC_IP${NC}"
    fi
}

# 生成自签名证书，直到用户输入有效密码
function generate_ssl_certificate() {
    echo -e "${YELLOW}>>> 生成自签名 SSL 证书...${NC}"

    while true; do
        read -rp "请输入要为证书设置的密码（至少6位）: " keystore_password

        if [ ${#keystore_password} -lt 6 ]; then
            echo -e "${RED}>>> 密码太短，至少需要6位字符。请重新输入。${NC}"
        else
            break
        fi
    done

    # 获取服务器的公网 IP
    get_public_ip

    keytool -genkeypair -alias sillytavern -keyalg RSA -keysize 2048 -keystore "$KEYSTORE_FILE" -validity 365 \
        -storepass "$keystore_password" -keypass "$keystore_password" \
        -dname "CN=$PUBLIC_IP, OU=SillyTavern, O=SillyTavern, L=City, S=State, C=US"

    if [ $? -ne 0 ]; then
        echo -e "${RED}>>> 证书生成失败${NC}"
        exit 1
    fi

    echo -e "${GREEN}>>> 证书生成成功并保存在: $KEYSTORE_FILE${NC}"

    # 配置 application.properties 文件
    configure_application_properties "$keystore_password"
}

# 配置 application.properties 文件，启用 HTTPS
function configure_application_properties() {
    keystore_password=$1

    echo -e "${YELLOW}>>> 配置 application.properties 文件...${NC}"
    # 写入 SSL 配置到 application.properties
    echo "
# HTTPS 配置
server.port=8443
server.ssl.key-store=classpath:keystore.jks
server.ssl.key-store-password=$keystore_password
server.ssl.key-password=$keystore_password
" >> "$APPLICATION_PROPERTIES_FILE"

    echo -e "${GREEN}>>> application.properties 配置已更新${NC}"
}

# 使用 Spring Security 的 BCryptPasswordEncoder 来加密密码
function encrypt_password() {
    password=$1
    # 使用 Java 来执行 BCrypt 加密
    ENCRYPTED_PASSWORD=$(java -cp "target/classes:target/dependency/*" org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder "$password")
    echo "$ENCRYPTED_PASSWORD"
}

# 更新 application.properties 文件中的用户名和加密后的密码
function update_login_credentials() {
    read -rp "请输入新的用户名: " username

    while true; do
        echo -n "请输入新的密码: "
        password=$(hidden_password_input)
        echo

        echo -n "请确认新的密码: "
        confirm_password=$(hidden_password_input)
        echo

        if [[ "$password" == "$confirm_password" ]]; then
            echo -e "${GREEN}>>> 密码已确认。${NC}"
            break
        else
            echo -e "${RED}>>> 两次输入的密码不一致，请重新输入。${NC}"
        fi
    done

    # 使用 Java 来加密密码
    encrypted_password=$(encrypt_password "$password")

    if [ -f "$APPLICATION_PROPERTIES_FILE" ]; then
        echo -e "${YELLOW}>>> 正在修改 application.properties 中的用户名和密码...${NC}"

        # 更新 application.properties 中的用户名和密码
        sed -i "s/^app.security.username=.*/app.security.username=$username/" "$APPLICATION_PROPERTIES_FILE"
        sed -i "s/^app.security.password=.*/app.security.password=$encrypted_password/" "$APPLICATION_PROPERTIES_FILE"

        echo -e "${GREEN}>>> 用户名和密码已成功更新！${NC}"
    else
        echo -e "${RED}>>> 找不到 application.properties 文件。${NC}"
    fi
}

# 克隆或更新项目
function update_project() {
    if [ -d "$PROJECT_NAME" ]; then
        echo -e "${YELLOW}>>> 项目文件夹已存在，拉取最新代码...${NC}"
        cd "$PROJECT_NAME" || exit
        git pull
    else
        echo -e "${YELLOW}>>> 克隆GitHub项目...${NC}"
        git clone "$GITHUB_REPO_URL"
        cd "$PROJECT_NAME" || exit
    fi
}

# 构建项目
function build_project() {
    echo -e "${YELLOW}>>> 构建项目...${NC}"
    mvn clean install
}

# 找到最新的 JAR 文件
function find_latest_jar() {
    JAR_FILE=$(find target -name "*.jar" | head -n 1)
    echo -e "${GREEN}>>> 找到的 JAR 文件: $JAR_FILE${NC}"
}

# 创建/更新 systemd 服务文件
function setup_service() {
    if [ ! -f "$SERVICE_FILE" ]; then
        echo -e "${YELLOW}>>> 创建 systemd 服务文件...${NC}"
        sudo bash -c 'cat <<EOF > '"$SERVICE_FILE"'
[Unit]
Description=Spring Boot Application for '"$PROJECT_NAME"'
After=network.target

[Service]
User=root
WorkingDirectory=/root/'"$PROJECT_NAME"'
ExecStart=/usr/bin/java -jar /root/'"$PROJECT_NAME"'/'"$JAR_FILE"' > /var/log/'"$APP_NAME"'.log 2>&1
SuccessExitStatus=143
TimeoutStopSec=10
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF'
    else
        echo -e "${GREEN}>>> systemd 服务文件已存在，跳过创建步骤...${NC}"
    fi
}

# 启动或重启服务
function start_or_restart_service() {
    echo -e "${YELLOW}>>> 启动或重启 Spring Boot 应用服务...${NC}"
    sudo systemctl daemon-reload
    sudo systemctl restart "$APP_NAME"
    sudo systemctl enable "$APP_NAME"
    echo -e "${GREEN}>>> 服务已启动/重启成功！${NC}"
}

# 检查服务状态
function check_service_status() {
    echo -e "${YELLOW}>>> 检查服务状态...${NC}"
    sudo systemctl status "$APP_NAME"
}

# 检查是否已部署，包括证书文件
function is_deployed() {
    if [ -d "$PROJECT_NAME" ] && [ -f "$SERVICE_FILE" ] && [ -f "$KEYSTORE_FILE" ]; then
        return 0  # 已部署
    else
        return 1  # 未部署或证书缺失
    fi
}

# 执行初次部署流程
function deploy_project() {
    update_system
    install_dependencies
    check_installation
    update_project
    generate_ssl_certificate  # 生成 SSL 证书并配置 HTTPS
    build_project
    find_latest_jar
    setup_service
    start_or_restart_service
    check_service_status
}

# 显示菜单并获取用户选择
function show_menu() {
    echo -e "${BLUE}╔══════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║               ${GREEN}请选择一个操作${NC}${BLUE}                    ║${NC}"
    echo -e "${BLUE}╠══════════════════════════════════════════════════╣${NC}"
    echo -e "${BLUE}║${NC} （1）启动/重启服务${BLUE}                              ║${NC}"
    echo -e "${BLUE}║${NC} （2）更新项目管理脚本${BLUE}                          ║${NC}"
    echo -e "${BLUE}║${NC} （3）更新项目${BLUE}                                  ║${NC}"
    echo -e "${BLUE}║${NC} （4）修改登录用户名和密码${BLUE}                      ║${NC}"
    echo -e "${BLUE}╠══════════════════════════════════════════════════╣${NC}"
    echo -e "${BLUE}║${RED} （0）退出${NC}${BLUE}                                      ║${NC}"
    echo -e "${BLUE}╚══════════════════════════════════════════════════╝${NC}"
    read -rp "$(echo -e "${BLUE}输入你的选择 [0-4]: ${NC}")" choice
    case $choice in
        1)
            echo -e "${YELLOW}>>> 你选择了启动/重启服务${NC}"
            start_or_restart_service
            check_service_status
            ;;
        2)
            echo -e "${YELLOW}>>> 你选择了更新项目管理脚本${NC}"
            update_script
            ;;
        3)
            echo -e "${YELLOW}>>> 你选择了更新项目${NC}"
            update_project
            build_project
            find_latest_jar
            setup_service
            start_or_restart_service
            check_service_status
            ;;
        4)
            echo -e "${YELLOW}>>> 你选择了修改登录用户名和密码${NC}"
            update_login_credentials
            ;;
        0)
            echo -e "${RED}>>> 退出脚本${NC}"
            exit 0
            ;;
        *)
            echo -e "${RED}>>> 无效的选择，请重新选择${NC}"
            show_menu
            ;;
    esac
}

# 主流程：检查是否已部署，包括证书文件
show_header
if is_deployed; then
    echo -e "${GREEN}>>> 项目已部署。${NC}"
    show_menu  # 如果项目已部署，则显示菜单
else
    echo -e "${YELLOW}>>> 项目尚未部署，开始部署流程...${NC}"
    deploy_project  # 如果项目尚未部署，执行完整的部署流程
fi