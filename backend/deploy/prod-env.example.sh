export SPRING_PROFILES_ACTIVE=prod
export SERVER_PORT=8080
export LOG_DIR=/opt/scoring/logs

export DB_URL='jdbc:mysql://127.0.0.1:3306/scoring_mvp?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export DB_USERNAME='scoring_app'
export DB_PASSWORD='change-this-password'

export JWT_SECRET='change-this-long-random-secret'
export JWT_EXPIRE_SECONDS='2592000'
export WECHAT_APP_ID='wx8113b05d52ef52b3'
export WECHAT_APP_SECRET='change-this-wechat-secret'

export CORS_ALLOWED_ORIGIN_1='https://your-h5.example.com'
export CORS_ALLOWED_ORIGIN_2='https://api.example.com'
export RATE_LIMIT_ENABLED='true'
export LOGIN_LIMIT_PER_MINUTE='20'
export WRITE_LIMIT_PER_MINUTE='60'
