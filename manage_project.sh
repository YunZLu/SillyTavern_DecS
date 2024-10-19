#!/bin/bash

# 更新后的 GitHub 仓库地址和项目名
GITHUB_REPO_URL="https://github.com/YunZLu/SillyTavern_DecS.git"
PROJECT_NAME="SillyTavern_DecS"
APP_NAME="sillytavern-decs-app"
SERVICE_FILE="/etc/systemd/system/$APP_NAME.service"
SCRIPT_URL="https://raw.githubusercontent.com/YunZLu/SillyTavern_DecS/refs/heads/main/manage_project.sh"
CONFIG_PATH="/etc/$APP_NAME/config.json"

# 更改默认的服务端口，避免使用常用的 8080 端口
DEFAULT_PORT=8445

GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 检测防火墙工具
if command -v ufw &> /dev/null; then
    FIREWALL_TOOL="ufw"
elif command -v firewall-cmd &> /dev/null; then
    FIREWALL_TOOL="firewalld"
else
    FIREWALL_TOOL="none"
fi

# 显示标题
function show_header() {
    echo -e "${BLUE}╔══════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║              ${GREEN}欢迎使用酒馆解密服务脚本${NC}${BLUE}            ║${NC}"
    echo -e "${BLUE}╚══════════════════════════════════════════════════╝${NC}"
}

# 通用重试函数，支持自定义最大重试次数和延迟
function retry_function() {
    local func="$1"
    local max_attempts="${2:-5}"  # 默认5次
    local delay="${3:-2}"         # 默认延迟2秒
    local attempt=1

    while (( attempt <= max_attempts )); do
        "$func" && return 0  # 执行传入的函数
        echo -e "${RED}>>> 失败: $func，尝试第 $attempt 次...${NC}"
        sleep "$delay"
        ((attempt++))
    done

    echo -e "${RED}>>> 超过最大重试次数，操作失败: $func${NC}"
    return 1
}

# 安装并验证依赖
function install_and_check_dependencies() {
    echo -e "${YELLOW}>>> 安装并验证依赖: Git, openjdk-17-jdk, Maven, curl, ufw, jq...${NC}"
    sudo apt-get install -y git openjdk-17-jdk maven curl ufw jq

    # 验证安装
    echo -e "${YELLOW}>>> 验证安装...${NC}"
    java -version && mvn -version && jq --version
}

# 放行端口
function allow_port() {
    echo -e "${YELLOW}>>> 放行 $DEFAULT_PORT 端口...${NC}"

    case $FIREWALL_TOOL in
        ufw)
            sudo ufw allow "$DEFAULT_PORT"/tcp
            echo -e "${GREEN}>>> 端口 $DEFAULT_PORT 已通过 ufw 放行。${NC}"
            ;;
        firewalld)
            sudo firewall-cmd --zone=public --add-port="$DEFAULT_PORT"/tcp --permanent
            sudo firewall-cmd --reload
            echo -e "${GREEN}>>> 端口 $DEFAULT_PORT 已通过 firewalld 放行。${NC}"
            ;;
        none)
            echo -e "${RED}>>> 未检测到防火墙工具，请手动放行端口。${NC}"
            ;;
    esac
}

# 检查是否已部署
function is_deployed() {
    if [ -f "$SERVICE_FILE" ]; then
        return 0  # 已部署
    else
        return 1  # 未部署
    fi
}

# 停止服务的通用函数
function stop_service() {
    if systemctl is-active --quiet "$APP_NAME"; then
        echo -e "${YELLOW}>>> 停止现有服务...${NC}"
        sudo systemctl stop "$APP_NAME"
    else
        echo -e "${YELLOW}>>> 服务未运行，无需停止。${NC}"
    fi
}

# 移动配置文件并设置环境变量
function move_config_and_set_env() {
    cd "/root/$PROJECT_NAME" || exit
    if [ -f "src/main/resources/config.json" ]; then
        sudo mkdir -p /etc/$APP_NAME/
        sudo cp src/main/resources/config.json "$CONFIG_PATH"
        sudo chmod 644 "$CONFIG_PATH"
        echo "CONFIG_JSON_PATH=$CONFIG_PATH" | sudo tee -a /etc/environment > /dev/null
        export CONFIG_JSON_PATH=$CONFIG_PATH  # 导出变量到当前环境
        source /etc/environment  # 确保当前shell读取到最新的环境变量
        echo -e "${GREEN}>>> config.json 已移动至 $CONFIG_PATH 并设置环境变量${NC}"
    else
        echo -e "${RED}>>> 未找到 config.json 文件，跳过移动步骤。${NC}"
    fi
}

# 设置同IP并发限制
function set_concurrent_requests() {
    local current_limit
    current_limit=$(jq -r '.maxConcurrentRequestsPerIP' "$CONFIG_PATH")
    
    echo -e "${YELLOW}当前同IP并发请求限制: $current_limit${NC}"
    read -rp "请输入新的同IP并发请求限制: " new_limit
    jq '.maxConcurrentRequestsPerIP = '"$new_limit" "$CONFIG_PATH" > tmp.$$.json && mv tmp.$$.json "$CONFIG_PATH"
    echo -e "${GREEN}同IP并发请求限制已更新！${NC}"
    touch "$CONFIG_PATH"
}

# 修改私钥
function set_private_key() {
    local current_private_key
    current_private_key=$(jq -r '.privateKey' "$CONFIG_PATH")

    echo -e "${YELLOW}当前私钥: ${current_private_key:0:50}...(已隐藏)${NC}"
    echo -e "${YELLOW}请输入新的私钥内容（多行输入，按 Ctrl+D 完成输入）:${NC}"

    # 捕获多行输入的私钥，保留换行符
    private_key=$(</dev/stdin)
    
    # 用 jq 更新 privateKey，而不删除换行符和空格
    jq --arg privateKey "$private_key" '.privateKey = $privateKey' "$CONFIG_PATH" > tmp.$$.json && mv tmp.$$.json "$CONFIG_PATH"
    
    echo -e "${GREEN}私钥已更新！${NC}"
    touch "$CONFIG_PATH"
}

# 白名单设置页面
function whitelist_menu() {
    echo -e "${BLUE}╔══════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║                   ${GREEN}设置白名单${NC}${BLUE}                     ║${NC}"
    echo -e "${BLUE}╠══════════════════════════════════════════════════╣${NC}"
    echo -e "${BLUE}║${NC} （1）查看白名单${BLUE}                                  ║${NC}"
    echo -e "${BLUE}║${NC} （2）增加白名单${BLUE}                                  ║${NC}"
    echo -e "${BLUE}║${NC} （3）删除白名单${BLUE}                                  ║${NC}"
    echo -e "${BLUE}║${NC} （4）修改白名单${BLUE}                                  ║${NC}"
    echo -e "${BLUE}╠══════════════════════════════════════════════════╣${NC}"
    echo -e "${BLUE}║${RED} （0）返回主菜单${BLUE}                                  ║${NC}"
    echo -e "${BLUE}╚══════════════════════════════════════════════════╝${NC}"
    read -rp "$(echo -e "${BLUE}请输入你的选择 [0-4]: ${NC}")" whitelist_choice

    case $whitelist_choice in
        1)
            view_whitelist
            ;;
        2)
            add_whitelist
            ;;
        3)
            delete_whitelist
            ;;
        4)
            modify_whitelist
            ;;
        0)
            show_menu
            ;;
        *)
            echo -e "${RED}无效的选择${NC}"
            ;;
    esac
}

# 查看白名单
function view_whitelist() {
    local whitelist
    whitelist=$(jq -r '.whitelist[]' "$CONFIG_PATH")
    echo -e "${GREEN}当前白名单: ${NC}"
    echo "$whitelist" | nl  # 使用 nl 命令为白名单项编号
}

# 增加白名单
function add_whitelist() {
    read -rp "请输入要增加的白名单 URL: " new_entry
    jq '.whitelist += ["'"$new_entry"'"]' "$CONFIG_PATH" > tmp.$$.json && mv tmp.$$.json "$CONFIG_PATH"
    echo -e "${GREEN}白名单已增加: $new_entry${NC}"
    touch "$CONFIG_PATH"
}

# 删除白名单
function delete_whitelist() {
    view_whitelist
    read -rp "请输入要删除的白名单项编号: " number
    jq '.whitelist |= del(.[('"$number"'-1)])' "$CONFIG_PATH" > tmp.$$.json && mv tmp.$$.json "$CONFIG_PATH"
    echo -e "${GREEN}白名单项已删除${NC}"
    touch "$CONFIG_PATH"
}

# 修改白名单
function modify_whitelist() {
    view_whitelist
    read -rp "请输入要修改的白名单项编号: " number
    read -rp "请输入新的白名单 URL: " new_entry
    jq '.whitelist['"$number"'-1] = "'"$new_entry"'"' "$CONFIG_PATH" > tmp.$$.json && mv tmp.$$.json "$CONFIG_PATH"
    echo -e "${GREEN}白名单已修改为: $new_entry${NC}"
    touch "$CONFIG_PATH"
}

# 更新 systemd 服务文件
function setup_service() {
    stop_service  # 停止服务

    if [ -f "$SERVICE_FILE" ]; then
        echo -e "${YELLOW}>>> 已存在的 systemd 服务文件，正在删除...${NC}"
        sudo rm -f "$SERVICE_FILE"  # 删除旧的服务文件
        echo -e "${GREEN}>>> 旧的 systemd 服务文件已删除，正在重新构建服务...${NC}"
    fi

    # 创建新的 systemd 服务文件
    sudo bash -c 'cat <<EOF > '"$SERVICE_FILE"'
[Unit]
Description=Spring Boot Application for '"$PROJECT_NAME"'
After=network.target

[Service]
User=root
Environment=CONFIG_JSON_PATH='"$CONFIG_PATH"'
WorkingDirectory=/root/'"$PROJECT_NAME"'
ExecStart=/usr/bin/java -jar /root/'"$PROJECT_NAME"'/'"$JAR_FILE"' --server.port='"$DEFAULT_PORT"' > /var/log/'"$APP_NAME"'.log 2>&1
SuccessExitStatus=143
TimeoutStopSec=10
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF'
    echo -e "${GREEN}>>> 新的 systemd 服务文件已创建${NC}"
}

# 启动或重启服务
function start_or_restart_service() {
    echo -e "${YELLOW}>>> 启动或重启 Spring Boot 应用服务...${NC}"
    sudo systemctl daemon-reload
    sudo systemctl restart "$APP_NAME"
    sudo systemctl enable "$APP_NAME"
    echo -e "${GREEN}>>> 服务已启动/重启成功！${NC}"
}

# 部署项目函数
function deploy_project() {
    echo -e "${YELLOW}>>> 开始部署项目...${NC}"
    retry_function install_and_check_dependencies
    retry_function update_project
    retry_function allow_port
    retry_function build_project
    find_latest_jar
    move_config_and_set_env  # 移动 config.json
    set_private_key          # 提示用户修改私钥
    setup_service
    start_or_restart_service
    echo -e "${GREEN}>>> 项目部署完成。${NC}"
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
        cd "/root/$PROJECT_NAME" || exit
    fi
}

# 构建项目
function build_project() {
    echo -e "${YELLOW}>>> 构建项目...${NC}"
    mvn clean install
}

# 找到最新的 JAR 文件
function find_latest_jar() {
    JAR_FILE=$(find target -name "*.jar" | head -n 1 2>/dev/null)
    echo -e "${GREEN}>>> 找到的 JAR 文件: $JAR_FILE${NC}"
}

# 查看服务日志
function view_service_logs() {
    echo -e "${YELLOW}>>> 查看服务日志...${NC}"
    sudo journalctl -u "$APP_NAME" -f
}

# 完全卸载服务
function uninstall_service() {
    if ! is_deployed; then
        echo -e "${RED}>>> 项目尚未部署，无法执行卸载操作。${NC}"
        return
    fi

    stop_service

    echo -e "${YELLOW}>>> 删除 systemd 服务文件...${NC}"
    sudo rm -f "$SERVICE_FILE"

    echo -e "${YELLOW}>>> 删除项目文件夹...${NC}"
    sudo rm -rf "/root/$PROJECT_NAME"

    echo -e "${YELLOW}>>> 删除外部配置文件...${NC}"
    sudo rm -f "$CONFIG_PATH"

    echo -e "${YELLOW}>>> 删除环境变量 CONFIG_JSON_PATH...${NC}"
    sudo sed -i '/CONFIG_JSON_PATH/d' /etc/environment
    source /etc/environment

    echo -e "${GREEN}>>> 服务及相关文件已完全卸载。${NC}"
    cd /root
}

# 更新脚本
function update_script() {
    echo -e "${YELLOW}>>> 正在检查脚本更新...${NC}"
    TEMP_SCRIPT=$(mktemp)
    curl -o "$TEMP_SCRIPT" "$SCRIPT_URL"
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}>>> 脚本已更新成功，正在重启脚本...${NC}"
        chmod +x "$TEMP_SCRIPT"
        mv "$TEMP_SCRIPT" /root/manage_project.sh
        exec /root/manage_project.sh
    else
        echo -e "${RED}>>> 脚本更新失败，保留原脚本。请检查网络连接。${NC}"
        rm -f "$TEMP_SCRIPT"
    fi
}

# 显示菜单并获取用户选择
function show_menu() {
    echo -e ""
    echo -e "${BLUE}╔══════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║                     ${GREEN}主菜单${NC}${BLUE}                       ║${NC}"
    echo -e "${BLUE}╠══════════════════════════════════════════════════╣${NC}"
    echo -e "${BLUE}║${NC} （1）启动/重启服务        ${BLUE}                       ║${NC}"
    echo -e "${BLUE}║${NC} （2）查看服务日志        ${BLUE}                        ║${NC}"
    echo -e "${BLUE}║${NC} （3）设置白名单          ${BLUE}                        ║${NC}"
    echo -e "${BLUE}║${NC} （4）设置同IP并发限制     ${BLUE}                       ║${NC}"
    echo -e "${BLUE}║${NC} （5）设置私钥            ${BLUE}                        ║${NC}"
    echo -e "${BLUE}║${NC} （6）更新项目管理脚本      ${BLUE}                      ║${NC}"
    echo -e "${BLUE}║${NC} （7）安装/更新服务        ${BLUE}                       ║${NC}"
    echo -e "${BLUE}║${NC} （8）完全卸载服务         ${BLUE}                       ║${NC}"
    echo -e "${BLUE}╠══════════════════════════════════════════════════╣${NC}"
    echo -e "${BLUE}║${RED} （0）退出                ${BLUE}                        ║${NC}"
    echo -e "${BLUE}╚══════════════════════════════════════════════════╝${NC}"
    read -rp "$(echo -e "${BLUE}输入你的选择 [0-8]: ${NC}")" choice

    # 检查是否已部署
    if [[ $choice -ge 1 && $choice -le 5 ]]; then
        if ! is_deployed; then
            echo -e "${RED}>>> 项目尚未部署，请先部署项目。${NC}"
            return
        fi
    fi

    case $choice in
        1)
            retry_function start_or_restart_service
            ;;
        2)
            view_service_logs
            ;;
        3)
            whitelist_menu
            ;;
        4)
            set_concurrent_requests
            ;;
        5)
            set_private_key
            ;;
        6)
            retry_function update_script
            ;;
        7)
            if is_deployed; then
                echo -e "${YELLOW}>>> 服务已部署，开始更新...${NC}"
                retry_function update_project
                retry_function build_project
                find_latest_jar
                setup_service
                start_or_restart_service
            else
                echo -e "${YELLOW}>>> 服务尚未部署，开始部署...${NC}"
                deploy_project
            fi
            ;;
        8)
            uninstall_service
            ;;
        0)
            exit 0
            ;;
        *)
            echo -e "${RED}无效的选择，请重新选择${NC}"
            ;;
    esac
}

# 主流程：检查是否已部署
show_header
if is_deployed; then
    echo -e "${GREEN}>>> 项目已部署。${NC}"
    while true; do
        show_menu
    done
else
    echo -e "${YELLOW}>>> 项目尚未部署，开始部署流程...${NC}"
    deploy_project
    while true; do
        show_menu
    done
fi
