export SPRING_PROFILES_ACTIVE=prod
export SERVER_PORT=8080
export LOG_DIR=/opt/scoring/logs

export DB_URL='jdbc:mysql://127.0.0.1:3306/scoring_mvp?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export DB_USERNAME='scoring_app'
export DB_PASSWORD='change-this-password'

export JWT_SECRET='change-this-long-random-secret'
export JWT_EXPIRE_SECONDS='2592000'
# 注意：真实 AppID 属公开信息（随小程序客户端分发），但模板请保持占位符；
# 真实值见 frontend/src/manifest.json。
export WECHAT_APP_ID='change-this-appid'
export WECHAT_APP_SECRET='change-this-wechat-secret'
export UPLOAD_DIR='/opt/scoring/uploads'
export UPLOAD_PUBLIC_BASE_URL='https://api.eunomia.cc'

# 经过 nginx 反代时源 IP 是 127.0.0.1，需要信任代理头才能按真实 IP 限流；
# 置 true 前必须确认：nginx 已用 `proxy_set_header X-Real-IP $remote_addr` 覆盖该头，
# 且后端 ClientIpResolver 已改为只信 X-Real-IP（见审查文档 P1-1，否则 XFF 首段可伪造绕过限流）。
export TRUST_PROXY_HEADERS='false'
export CORS_ALLOWED_ORIGIN_1='https://eunomia.cc'
export CORS_ALLOWED_ORIGIN_2='https://www.eunomia.cc'
export RATE_LIMIT_ENABLED='true'
export LOGIN_LIMIT_PER_MINUTE='20'
export WRITE_LIMIT_PER_MINUTE='60'
