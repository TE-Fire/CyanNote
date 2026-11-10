#!/usr/bin/env bash
# 单体联调版接口自检：按前端 27 个调用逐条打一遍，只看 success 与关键字段。
# 用法：先启动服务（端口 8000），再执行 bash smoke.sh
B=http://127.0.0.1:8000

req() {
  local path="$1" body="$2" token="$3"
  local args=(-s --noproxy '*' -X POST "$B$path" -H "Content-Type: application/json")
  [ -n "$body" ] && args+=(-d "$body")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  curl "${args[@]}"
}

show() {
  printf '%-42s ' "$1"
  shift
  "$@" | head -c 150
  echo
}

echo "=== 无 token 部分 ==="
show "channel/list"        req /note/channel/list '{}'
show "topic/list"          req /note/topic/list '{"keyword":""}'
show "discover/note/list"  req /note/discover/note/list '{"channelId":0,"pageNo":1}'
show "note/detail(1001)"   req /note/note/detail '{"id":1001}'
show "note/1001/comments"  req /comment/comment/list '{"noteId":1001,"pageNo":1}'
show "user/profile(2)"     req /user/user/profile '{"userId":2}'
show "search/note"         req /search/search/note '{"keyword":"巷子","pageNo":1}'
show "search/user"         req /search/search/user '{"keyword":"阿柚","pageNo":1}'
show "401 未登录发笔记"     req /note/note/publish '{"title":"x"}'

echo
echo "=== 登录（seed 用户 1）==="
# 验证码由服务端随机生成并随响应返回，先取后登，与前端行为一致
CODE=$(req /auth/verification/code/send '{"phone":"13800000001"}' | sed -n 's/.*"data":"\([0-9]\{6\}\)".*/\1/p')
echo "验证码=${CODE}"
LOGIN=$(req /auth/login "{\"phone\":\"13800000001\",\"code\":\"$CODE\"}")
echo "$LOGIN"
TOKEN=$(echo "$LOGIN" | sed -n 's/.*"data":"\([^"]*\)".*/\1/p')
echo "token=${TOKEN:0:12}..."

echo
echo "=== 带 token 部分 ==="
show "note/1001/like"       req /note/note/like '{"id":1001}' "$TOKEN"
show "isLikedAndCollected"  req /note/note/isLikedAndCollectedData '{"noteId":1001}' "$TOKEN"
show "note/1001/unlike"     req /note/note/unlike '{"id":1001}' "$TOKEN"
show "note/1001/collect"    req /note/note/collect '{"id":1001}' "$TOKEN"
show "note/1001/uncollect"  req /note/note/uncollect '{"id":1001}' "$TOKEN"
show "follow 用户 3"        req /relation/relation/follow '{"followUserId":3}' "$TOKEN"
show "following/list"       req /relation/relation/following/list '{"userId":1,"pageNo":1}' "$TOKEN"
show "fans/list"            req /relation/relation/fans/list '{"userId":1,"pageNo":1}' "$TOKEN"
show "profile/note/list"    req /note/profile/note/list '{"type":1,"userId":1,"pageNo":1}' "$TOKEN"
show "comment/publish"      req /comment/comment/publish '{"noteId":1001,"content":"自检评论"}' "$TOKEN"
show "comment/like"         req /comment/comment/like '{"commentId":5001}' "$TOKEN"
show "comment/unlike"       req /comment/comment/unlike '{"commentId":5001}' "$TOKEN"
show "comment/child/list"   req /comment/comment/child/list '{"parentCommentId":5001,"pageNo":1}' "$TOKEN"
show "logout"               req /auth/logout '{}' "$TOKEN"

echo
echo "=== 新用户：登录即注册 → 更新资料 → 发布 ==="
NCODE=$(req /auth/verification/code/send '{"phone":"13900000099"}' | sed -n 's/.*"data":"\([0-9]\{6\}\)".*/\1/p')
NEW=$(req /auth/login "{\"phone\":\"13900000099\",\"code\":\"$NCODE\"}")
NTOKEN=$(echo "$NEW" | sed -n 's/.*"data":"\([^"]*\)".*/\1/p')
echo "新用户 token=${NTOKEN:0:12}..."

show "user/update(multipart)" curl -s --noproxy '*' -X POST "$B/user/user/update" \
  -H "Authorization: Bearer $NTOKEN" -F "nickname=自检用户" -F "introduction=联调测试" -F "sex=1"

printf 'x' > /tmp/mono-test.png
show "oss/file/upload" curl -s --noproxy '*' -X POST "$B/oss/file/upload" \
  -H "Authorization: Bearer $NTOKEN" -F "file=@/tmp/mono-test.png"

show "note/publish" req /note/note/publish \
  '{"type":0,"title":"自检发布","content":"来自 smoke.sh","channelId":4,"imgUris":[],"topics":[{"id":2,"name":"城市散步"}]}' "$NTOKEN"

echo
echo "=== 静态资源 ==="
printf '%-42s ' "cover/1001.svg"
curl -s --noproxy '*' -o /dev/null -w "HTTP %{http_code} %{content_type}\n" "$B/img/cover/1001.svg"
printf '%-42s ' "avatar/1.svg"
curl -s --noproxy '*' -o /dev/null -w "HTTP %{http_code} %{content_type}\n" "$B/img/avatar/1.svg"
