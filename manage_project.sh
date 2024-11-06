#!/bin/bash

# 更新后的 GitHub 仓库地址和项目名
GITHUB_REPO_URL="https://github.com/YunZLu/SillyTavern_DecS.git"
SCRIPT_URL="https://raw.githubusercontent.com/YunZLu/SillyTavern_DecS/refs/heads/main/manage_project.sh"
PROJECT_NAME="SillyTavern_DecS"
APP_NAME="sillytavern-decs-app"
CONFIG_PATH="/root/$PROJECT_NAME/config.json"
VENV_PATH="./venv"
DEFAULT_PORT=5050

GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 显示标题
function show_header() {
    echo -e "${BLUE}╔══════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║              ${GREEN}欢迎使用酒馆解密服务脚本${NC}${BLUE}            ║${NC}"
    echo -e "${BLUE}╚══════════════════════════════════════════════════╝${NC}"
}

# 通用重试函数
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
    echo -e "${YELLOW}>>> 安装并验证依赖: Git, Python3, pip3, virtualenv, curl, jq...${NC}"
    sudo apt-get install -y git python3 python3-pip python3-venv curl jq

    # 验证安装
    echo -e "${YELLOW}>>> 验证安装...${NC}"
    python3 --version && pip3 --version && jq --version
}

# 检查是否已部署
function is_deployed() {
    if [ -d "$PROJECT_NAME" ]; then
        return 0  # 已部署
    else
        return 1  # 未部署
    fi
}

# 创建 Python 虚拟环境并安装依赖
function setup_python_env() {
    echo -e "${YELLOW}>>> 创建 Python 虚拟环境并安装依赖...${NC}"

    # 创建虚拟环境
    if [ ! -d "$VENV_PATH" ]; then
        python3 -m venv "$VENV_PATH"
    fi

    # 激活虚拟环境并安装依赖
    source "$VENV_PATH/bin/activate"
    pip install --upgrade pip
    pip install -r ./requirements.txt
    deactivate
    cd /root
}

# 启动 Flask 服务
function start_flask_service() {
    echo -e "${YELLOW}>>> 启动 Flask 服务...${NC}"
    cd /root/$PROJECT_NAME

    if [ ! -d "$VENV_PATH" ]; then
        echo -e "${RED}>>> Python 虚拟环境未创建，无法启动服务。请先部署项目。${NC}"
        return 1
    fi


    source "$VENV_PATH/bin/activate"
    echo -e "${GREEN}>>> Flask 服务启动中，按 Ctrl+C 退出...${NC}"
    hypercorn main:app --bind 0.0.0.0:$DEFAULT_PORT
    deactivate
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

# 克隆或更新项目
function update_project() {
    if [ -d "$PROJECT_NAME" ]; then
        echo -e "${YELLOW}>>> 项目文件夹已存在，拉取最新代码...${NC}"
        cd "$PROJECT_NAME" || exit
        git pull
    else
        echo -e "${YELLOW}>>> 克隆 GitHub 项目...${NC}"
        git clone "$GITHUB_REPO_URL"
        cd "$PROJECT_NAME" || exit
    fi
}

# 完全卸载服务
function uninstall_service() {
    if ! is_deployed; then
        echo -e "${RED}>>> 项目尚未部署，无法执行卸载操作。${NC}"
        return
    fi

    echo -e "${YELLOW}>>> 删除项目文件夹...${NC}"
    sudo rm -rf "$PROJECT_NAME"

    echo -e "${YELLOW}>>> 删除 Python 虚拟环境...${NC}"
    sudo rm -rf "$VENV_PATH"

    echo -e "${YELLOW}>>> 删除配置文件...${NC}"
    sudo rm -f "$CONFIG_PATH"

    echo -e "${GREEN}>>> 服务及相关文件已完全卸载。${NC}"
    cd /root
}

# 显示菜单并获取用户选择
function show_menu() {
    echo -e ""
    echo -e "${BLUE}╔══════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║                     ${GREEN}主菜单${NC}${BLUE}                       ║${NC}"
    echo -e "${BLUE}╠══════════════════════════════════════════════════╣${NC}"
    echo -e "${BLUE}║${NC} （1）启动服务        ${BLUE}                            ║${NC}"
    echo -e "${BLUE}║${NC} （2）设置白名单      ${BLUE}                            ║${NC}"
    echo -e "${BLUE}║${NC} （3）设置同IP并发限制 ${BLUE}                           ║${NC}"
    echo -e "${BLUE}║${NC} （4）设置私钥        ${BLUE}                            ║${NC}"
    echo -e "${BLUE}║${NC} （5）更新服务        ${BLUE}                            ║${NC}"
    echo -e "${BLUE}║${NC} （6）更新管理脚本    ${BLUE}                            ║${NC}"
    echo -e "${BLUE}║${NC} （7）完全卸载服务    ${BLUE}                            ║${NC}"
    echo -e "${BLUE}╠══════════════════════════════════════════════════╣${NC}"
    echo -e "${BLUE}║${RED} （0）退出            ${BLUE}                            ║${NC}"
    echo -e "${BLUE}╚══════════════════════════════════════════════════╝${NC}"
    read -rp "$(echo -e "${BLUE}输入你的选择 [0-7]: ${NC}")" choice

    case $choice in
        1)
            start_flask_service
            cd /root
            ;;
        2)
            whitelist_menu
            ;;
        3)
            set_concurrent_requests
            ;;
        4)
            set_private_key
            ;;
        5)
            update_project
            ;;
        6)
            update_script
            ;;
        7)
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
    echo -e "\n${GREEN}>>> 项目已部署。${NC}"
    while true; do
        show_menu
    done
else
    echo -e "${YELLOW}>>> 项目尚未部署，开始部署流程...${NC}"
    retry_function install_and_check_dependencies
    retry_function update_project
    retry_function setup_python_env
    while true; do
        show_menu
    done
fi
