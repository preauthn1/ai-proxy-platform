#!/bin/bash
# ============================================================
# deploy.sh - 通用后端服务 systemd 安装与启动脚本
#
# 用途：
#   - 通过 env 配置参数（SERVICE_NAME 等）
#   - 运行时逻辑：
#     1. 加载 env，再补默认值
#     2. 校验 root / java / jar / env 文件
#     3. 确保运行用户与目录权限
#     4. unit 不存在或内容有变更时写入并 daemon-reload
#     5. enable + start/restart
#
# 使用方式：
#   1. 准备 env 文件（推荐 /etc/<name>/<name>.env）
#   2. 运行此脚本（需 root）： ./deploy.sh
#
# 关键 env 变量（可在 env 文件或 export 设置）：
#   SERVICE_NAME            服务名（决定 service 文件名）
#   JAR_NAME                jar 文件名
#   APP_HOME                安装目录
#   ENV_FILE                环境变量文件路径（给 service 引用）
#   JAVA_CMD                java 可执行路径
#   JAVA_OPTS               JVM 参数
#   SPRING_PROFILES_ACTIVE
#   FORCE_UNIT=1            强制重写 unit（可选）
#
# 示例：
#   SERVICE_NAME=java-admin JAR_NAME=java-admin-api.jar \
#   APP_HOME=/opt/java-admin ENV_FILE=/etc/java-admin/java-admin.env \
#   ./deploy.sh
#
#   或直接：
#   source /etc/java-admin/java-admin.env && ./deploy.sh
# ============================================================

set -euo pipefail

# 加载 env 配置（默认值在 load 之后再补）
load_env() {
  if [[ -f "${ENV_FILE:-}" ]]; then
    echo "[INFO] Loading env from $ENV_FILE"
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
  elif [[ -f "./.env" ]]; then
    echo "[INFO] Loading env from .env"
    set -a
    # shellcheck disable=SC1091
    # 必须用 ./.env：无 / 的路径在 sh/POSIX 模式下只搜 PATH，不会找当前目录
    source ./.env
    set +a
  fi
}

apply_defaults() {
  : "${SERVICE_NAME:=backend}"
  : "${JAR_NAME:=app.jar}"
  : "${APP_HOME:=/opt/${SERVICE_NAME}}"
  : "${ENV_FILE:=/etc/${SERVICE_NAME}/${SERVICE_NAME}.env}"
  : "${JAVA_CMD:=/usr/bin/java}"
  : "${SPRING_PROFILES_ACTIVE:=prod}"
  : "${JAVA_OPTS:=-Xms512m -Xmx2g -XX:+UseG1GC}"
  : "${FORCE_UNIT:=0}"
  # 业务时区固定东八区；env 已写 -Duser.timezone= 时不覆盖
  if [[ "${JAVA_OPTS}" != *"-Duser.timezone="* ]]; then
    JAVA_OPTS="${JAVA_OPTS} -Duser.timezone=Asia/Shanghai"
  fi
}

require_root() {
  if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
    echo "[ERROR] Must run as root" >&2
    exit 1
  fi
}

validate() {
  if [[ ! -x "$JAVA_CMD" ]] && ! command -v "$JAVA_CMD" &>/dev/null; then
    echo "[ERROR] JAVA_CMD not executable: $JAVA_CMD" >&2
    exit 1
  fi
  if [[ ! -f "${APP_HOME}/${JAR_NAME}" ]]; then
    echo "[ERROR] JAR not found: ${APP_HOME}/${JAR_NAME}" >&2
    exit 1
  fi
  if [[ ! -f "$ENV_FILE" ]]; then
    echo "[ERROR] ENV_FILE not found: $ENV_FILE" >&2
    exit 1
  fi
  # 服务名只允许简单标识，避免写坏 unit / 用户名
  if [[ ! "$SERVICE_NAME" =~ ^[a-zA-Z0-9_-]+$ ]]; then
    echo "[ERROR] Invalid SERVICE_NAME: $SERVICE_NAME" >&2
    exit 1
  fi
}

# 生成 service 文件内容（使用当前 env 的实际值）
generate_service_content() {
  cat <<EOF
[Unit]
Description=${SERVICE_NAME} Backend Service
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=${SERVICE_NAME}
Group=${SERVICE_NAME}

WorkingDirectory=${APP_HOME}

# 环境变量文件（通过 env 配置）
EnvironmentFile=-${ENV_FILE}

# 启动命令
Environment=SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}
Environment="JAVA_OPTS=${JAVA_OPTS}"
ExecStart=${JAVA_CMD} \$JAVA_OPTS -jar ${APP_HOME}/${JAR_NAME}

SuccessExitStatus=143
Restart=always
RestartSec=5
TimeoutStopSec=30
KillSignal=SIGTERM

StandardOutput=journal
StandardError=journal
SyslogIdentifier=${SERVICE_NAME}

LimitNOFILE=65535
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
EOF
}

ensure_user_and_dirs() {
  if ! id "${SERVICE_NAME}" &>/dev/null; then
    echo "[INFO] Creating system user: ${SERVICE_NAME}"
    useradd -r -s /bin/false "${SERVICE_NAME}"
  fi

  mkdir -p "${APP_HOME}" "/var/log/${SERVICE_NAME}"
  chown -R "${SERVICE_NAME}:${SERVICE_NAME}" "${APP_HOME}" "/var/log/${SERVICE_NAME}"

  if [[ -f "$ENV_FILE" ]]; then
    chmod 600 "$ENV_FILE"
  fi
}

# unit 不存在或内容变化时写入
ensure_service_file() {
  local service_file="/etc/systemd/system/${SERVICE_NAME}.service"
  local tmp
  tmp="$(mktemp)"
  generate_service_content >"$tmp"

  if [[ -f "$service_file" && "$FORCE_UNIT" != "1" ]] && cmp -s "$tmp" "$service_file"; then
    echo "[INFO] Service file unchanged: $service_file"
    rm -f "$tmp"
    return 0
  fi

  if [[ -f "$service_file" ]]; then
    echo "[INFO] Updating service file: $service_file"
  else
    echo "[INFO] Generating service file: $service_file"
  fi
  echo "[INFO] SERVICE_NAME=${SERVICE_NAME}"
  echo "[INFO] JAR=${APP_HOME}/${JAR_NAME}"
  echo "[INFO] ENV_FILE=${ENV_FILE}"

  install -m 644 "$tmp" "$service_file"
  rm -f "$tmp"

  systemctl daemon-reload
  echo "[INFO] systemd daemon-reload done."
}

ensure_enabled() {
  systemctl enable "${SERVICE_NAME}" --quiet || true
}

start_or_restart_service() {
  ensure_enabled
  if systemctl is-active --quiet "${SERVICE_NAME}"; then
    echo "[INFO] ${SERVICE_NAME} is already running, performing restart..."
    systemctl restart "${SERVICE_NAME}"
  else
    echo "[INFO] ${SERVICE_NAME} is not running, starting..."
    systemctl start "${SERVICE_NAME}"
  fi

  echo "[INFO] Current service status:"
  systemctl status "${SERVICE_NAME}" --no-pager || true
}

setup() {
  require_root
  load_env
  apply_defaults
  echo "SERVICE_NAME: ${SERVICE_NAME}"
  validate
  ensure_user_and_dirs
  ensure_service_file
}

main() {
  local cmd="${1:-run}"

  case "$cmd" in
    run|start|install|"")
      echo "=== Backend Service Setup ==="
      setup
      start_or_restart_service
      echo "=== Done ==="
      ;;
    restart)
      setup
      ensure_enabled
      echo "[INFO] Restarting ${SERVICE_NAME}..."
      systemctl restart "${SERVICE_NAME}"
      systemctl status "${SERVICE_NAME}" --no-pager || true
      ;;
    stop)
      load_env
      apply_defaults
      echo "[INFO] Stopping ${SERVICE_NAME}..."
      systemctl stop "${SERVICE_NAME}"
      ;;
    status)
      load_env
      apply_defaults
      systemctl status "${SERVICE_NAME}" --no-pager || true
      ;;
    logs)
      load_env
      apply_defaults
      journalctl -u "${SERVICE_NAME}" -f --no-pager
      ;;
    *)
      echo "Usage: $0 [run|start|restart|stop|status|logs]"
      echo "  run/start  - 确保 unit/用户/权限，运行中则重启，否则启动"
      echo "  restart    - 同上后强制重启"
      echo "  stop       - 停止服务"
      echo "  status     - 查看状态"
      echo "  logs       - 跟踪 journal 日志"
      echo "  FORCE_UNIT=1 强制重写 unit"
      exit 1
      ;;
  esac
}

main "$@"
