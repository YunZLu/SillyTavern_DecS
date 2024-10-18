#!/bin/bash

# 更新后的 GitHub 仓库地址和项目名
GITHUB_REPO_URL="https://github.com/YunZLu/SillyTavern_DecS.git"
PROJECT_NAME="SillyTavern_DecS"
APP_NAME="sillytavern-decs-app"
SERVICE_FILE="/etc/systemd/system/$APP_NAME.service"
SCRIPT_URL="https://raw.githubusercontent.com/YunZLu/SillyTavern_DecS/refs/heads/main/manage_project.sh"

# 更改默认的服务端口，避免使用常用的 8080 端口
DEFAULT_PORT=8445

GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 显示标题
function show_header() {
    echo -e "${BLUE}╔═══════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║          ${GREEN}欢迎使用酒馆解密服务脚本${NC}${BLUE}                     ║${NC}"
    echo -e "${BLUE}╚═══════════════════════════════════════════════════════╝${NC}"
}

# 通用重试函数
function retry_function() {
    local max_attempts=5
    local attempt=1
    local delay=2

    while (( attempt <= max_attempts )); do
        "$@" && return 0  # 执行传入的函数
        echo -e "${RED}>>> 失败: $1，尝试第 $attempt 次...${NC}"
        sleep "$delay"  # 等待 2 秒
        ((attempt++))
    done

    echo -e "${RED}>>> 超过最大重试次数，操作失败: $1${NC}"
    return 1
}

# 更新系统包
function update_system() {
    echo -e "${YELLOW}>>> 更新系统包...${NC}"
    sudo apt-get update
}

# 安装依赖
function install_dependencies() {
    echo -e "${YELLOW}>>> 安装依赖: Git, openjdk-17-jdk, Maven, curl, ufw...${NC}"
    sudo apt-get install -y git openjdk-17-jdk maven curl ufw
}

# 验证安装
function check_installation() {
    echo -e "${YELLOW}>>> 验证 Java 和 Maven 安装...${NC}"
    java -version
    mvn -version
}

# 放行端口
function allow_port() {
    echo -e "${YELLOW}>>> 放行 $DEFAULT_PORT 端口...${NC}"
    
    if command -v ufw &> /dev/null; then
        # 使用 ufw
        sudo ufw allow "$DEFAULT_PORT"/tcp
        echo -e "${GREEN}>>> $DEFAULT_PORT 端口已成功放行。${NC}"
    elif command -v firewall-cmd &> /dev/null; then
        # 使用 firewalld
        sudo firewall-cmd --zone=public --add-port="$DEFAULT_PORT"/tcp --permanent
        sudo firewall-cmd --reload
        echo -e "${GREEN}>>> $DEFAULT_PORT 端口已成功放行。${NC}"
    else
        echo -e "${RED}>>> 未检测到可用的防火墙工具，请手动放行端口。${NC}"
    fi
}

# 克隆或更新项目
function update_project() {
    if [ -d "$PROJECT_NAME" ];then
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
    if [ ! -f "$SERVICE_FILE" ];then
        echo -e "${YELLOW}>>> 创建 systemd 服务文件...${NC}"
        sudo bash -c 'cat <<EOF > '"$SERVICE_FILE"'
[Unit]
Description=Spring Boot Application for '"$PROJECT_NAME"'
After=network.target

[Service]
User=root
WorkingDirectory=/root/'"$PROJECT_NAME"'
ExecStart=/usr/bin/java -jar /root/'"$PROJECT_NAME"'/'"$JAR_FILE"' --server.port='"$DEFAULT_PORT"' > /var/log/'"$APP_NAME"'.log 2>&1
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

# 查看服务后台日志
function view_service_logs() {
    echo -e "${YELLOW}>>> 查看服务日志...${NC}"
    sudo journalctl -u "$APP_NAME" -f
}

# 完全卸载服务
function uninstall_service() {
    echo -e "${YELLOW}>>> 正在停止服务...${NC}"
    sudo systemctl stop "$APP_NAME"
    
    echo -e "${YELLOW}>>> 正在禁用服务...${NC}"
    sudo systemctl disable "$APP_NAME"

    echo -e "${YELLOW}>>> 删除 systemd 服务文件...${NC}"
    sudo rm -f "$SERVICE_FILE"
    
    echo -e "${YELLOW}>>> 删除项目文件...${NC}"
    sudo rm -rf "/root/$PROJECT_NAME"

    echo -e "${GREEN}>>> 服务已完全卸载。${NC}"
}

# 更新脚本并安全覆盖旧脚本
function update_script() {
    echo -e "${YELLOW}>>> 正在检查脚本更新...${NC}"
    TEMP_SCRIPT=$(mktemp)  # 使用临时文件存放新脚本
    curl -o "$TEMP_SCRIPT" "$SCRIPT_URL"  # 下载新脚本到临时文件
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}>>> 脚本已更新成功，正在重启脚本...${NC}"
        chmod +x "$TEMP_SCRIPT"
        mv "$TEMP_SCRIPT" "$0"  # 用新脚本覆盖当前脚本
        exec "$0"  # 重新执行新脚本
    else
        echo -e "${RED}>>> 脚本更新失败，保留原脚本。请检查网络连接。${NC}"
        rm -f "$TEMP_SCRIPT"  # 删除临时文件
    fi
}

# 检查是否已部署
function is_deployed() {
    # 检查是否有构建好的 JAR 文件和 systemd 服务文件
    if [ -f "$SERVICE_FILE" ] && [ -n "$(find target -name '*.jar')" ]; then
        return 0  # 已部署
    else
        return 1  # 未部署
    fi
}

# 执行初次部署流程
function deploy_project() {
    retry_function update_system
    retry_function install_dependencies
    check_installation
    retry_function update_project
    retry_function allow_port  # 放行 $DEFAULT_PORT 端口
    retry_function build_project
    find_latest_jar
    setup_service
    start_or_restart_service
    check_service_status
    echo -e "${GREEN}>>> 初次部署完成。${NC}"
}

# 显示菜单并获取用户选择
function show_menu() {
    echo -e "${BLUE}╔══════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║               ${GREEN}请选择一个操作${NC}${BLUE}                    ║${NC}"
    echo -e "${BLUE}╠══════════════════════════════════════════════════╣${NC}"
    echo -e "${BLUE}║${NC} （1）启动/重启服务${BLUE}                             ║${NC}"
    echo -e "${BLUE}║${NC} （2）更新项目管理脚本${BLUE}                          ║${NC}"
    echo -e "${BLUE}║${NC} （3）更新服务${BLUE}                                 ║${NC}"
    echo -e "${BLUE}║${NC} （4）查看服务后台日志${BLUE}                          ║${NC}"
    echo -e "${BLUE}║${NC} （5）卸载服务${BLUE}                                 ║${NC}"
    echo -e "${BLUE}╠══════════════════════════════════════════════════╣${NC}"
    echo -e "${BLUE}║${RED} （0）退出${NC}${BLUE}                                      ║${NC}"
    echo -e "${BLUE}╚══════════════════════════════════════════════════╝${NC}"
    read -rp "$(echo -e "${BLUE}输入你的选择 [0-5]: ${NC}")" choice
    case $choice in
        1)
            echo -e "${YELLOW}>>> 你选择了启动/重启服务${NC}"
            retry_function start_or_restart_service
            check_service_status
            ;;
        2)
            echo -e "${YELLOW}>>> 你选择了更新项目管理脚本${NC}"
            retry_function update_script
            ;;
        3)
            echo -e "${YELLOW}>>> 你选择了更新服务${NC}"
            retry_function update_project
            retry_function build_project
            find_latest_jar
            setup_service
            start_or_restart_service
            check_service_status
            ;;
        4)
            echo -e "${YELLOW}>>> 你选择了查看服务后台日志${NC}"
            view_service_logs
            ;;
        5)
            echo -e "${RED}>>> 你选择了卸载服务${NC}"
            uninstall_service
            ;;
        0)
            echo -e "${RED}>>> 退出脚本${NC}"
            exit 0
            ;;
        *)
            echo -e "${RED}>>> 无效的选择，请重新选择${NC}"
            ;;
    esac
}

# 主流程：检查是否已部署
show_header
if is_deployed; then
    echo -e "${GREEN}>>> 项目已部署。${NC}"
    while true; do  # 用 while 循环包裹菜单
        show_menu  # 如果项目已部署，则显示菜单
    done
else
    echo -e "${YELLOW}>>> 项目尚未部署，开始部署流程...${NC}"
    deploy_project  # 如果项目尚未部署，执行完整的部署流程
    while true; do  # 部署完成后进入主菜单
        show_menu
    done
fi
