-- C-53 互动与收藏模块演示数据
-- 用途：本地/开发环境演示用户、已发布动态及互动数据。
-- 账号：demo_alice / demo_bob / demo_cici / demo_david / demo_ella
-- 密码：password
-- 可重复执行；固定 ID + INSERT IGNORE 不会重复插入。
-- 注意：该文件不会被 application.yml 自动执行，请按需手动导入。

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ---------------------------------------------------------------------------
-- 1. 演示用户
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO users
    (id, username, nickname, initials, password_hash, password_algorithm,
     avatar_url, profession, city, bio, status, created_at, updated_at)
VALUES
    ('usr_demo_alice', 'demo_alice', '林小满', '林',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'bcrypt',
     NULL, '产品设计师', '杭州', '记录设计灵感，也记录生活里的小确幸。', 'ACTIVE',
     UTC_TIMESTAMP(3) - INTERVAL 10 DAY, UTC_TIMESTAMP(3) - INTERVAL 1 DAY),
    ('usr_demo_bob', 'demo_bob', '周野', '周',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'bcrypt',
     NULL, '前端工程师', '深圳', '喜欢研究交互、代码和城市里的新鲜事。', 'ACTIVE',
     UTC_TIMESTAMP(3) - INTERVAL 9 DAY, UTC_TIMESTAMP(3) - INTERVAL 2 DAY),
    ('usr_demo_cici', 'demo_cici', '陈一一', '陈',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'bcrypt',
     NULL, '摄影爱好者', '成都', '用镜头收集平凡日子里的光。', 'ACTIVE',
     UTC_TIMESTAMP(3) - INTERVAL 8 DAY, UTC_TIMESTAMP(3) - INTERVAL 3 DAY),
    ('usr_demo_david', 'demo_david', '许知行', '许',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'bcrypt',
     NULL, '独立开发者', '上海', '保持好奇，持续做一些有意思的小工具。', 'ACTIVE',
     UTC_TIMESTAMP(3) - INTERVAL 7 DAY, UTC_TIMESTAMP(3) - INTERVAL 1 DAY),
    ('usr_demo_ella', 'demo_ella', '唐果', '唐',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'bcrypt',
     NULL, '编辑', '北京', '读书、散步、写下每一个值得记住的瞬间。', 'ACTIVE',
     UTC_TIMESTAMP(3) - INTERVAL 6 DAY, UTC_TIMESTAMP(3) - INTERVAL 4 HOUR);

-- ---------------------------------------------------------------------------
-- 2. 已发布动态：每位用户 2 条，共 10 条
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO posts
    (id, author_id, status, content, topic_id, media_type, like_count,
     comment_count, scheduled_at, published_at, edited_at, version,
     client_request_id, created_at, updated_at)
VALUES
    ('pst_demo_alice_01', 'usr_demo_alice', 'PUBLISHED',
     '今天把书桌重新整理了一遍，留出一块完整的空间给正在进行的设计。环境变简单之后，思路也跟着清晰了。',
     'tpc_seed_design', NULL, 4, 3, NULL,
     UTC_TIMESTAMP(3) - INTERVAL 5 DAY, NULL, 1, 'seed-demo-alice-01',
     UTC_TIMESTAMP(3) - INTERVAL 5 DAY, UTC_TIMESTAMP(3) - INTERVAL 5 DAY),
    ('pst_demo_alice_02', 'usr_demo_alice', 'PUBLISHED',
     '分享一个最近很喜欢的配色：雾蓝、米白和一点点橙色。安静里带着温度，做信息卡片特别合适。',
     'tpc_seed_design', NULL, 3, 3, NULL,
     UTC_TIMESTAMP(3) - INTERVAL 2 DAY, NULL, 1, 'seed-demo-alice-02',
     UTC_TIMESTAMP(3) - INTERVAL 2 DAY, UTC_TIMESTAMP(3) - INTERVAL 2 DAY),
    ('pst_demo_bob_01', 'usr_demo_bob', 'PUBLISHED',
     '把一个小组件从零做到可复用，最开心的不是代码变少，而是下一次遇到类似问题时终于有了更好的答案。',
     'tpc_seed_tech', NULL, 5, 3, NULL,
     UTC_TIMESTAMP(3) - INTERVAL 4 DAY, NULL, 1, 'seed-demo-bob-01',
     UTC_TIMESTAMP(3) - INTERVAL 4 DAY, UTC_TIMESTAMP(3) - INTERVAL 4 DAY),
    ('pst_demo_bob_02', 'usr_demo_bob', 'PUBLISHED',
     '周末在家做了一个极简的番茄钟，提醒自己专注工作，也提醒自己按时离开电脑去走走。',
     'tpc_seed_tech', NULL, 4, 3, NULL,
     UTC_TIMESTAMP(3) - INTERVAL 1 DAY, NULL, 1, 'seed-demo-bob-02',
     UTC_TIMESTAMP(3) - INTERVAL 1 DAY, UTC_TIMESTAMP(3) - INTERVAL 1 DAY),
    ('pst_demo_cici_01', 'usr_demo_cici', 'PUBLISHED',
     '傍晚的风把云吹成了很柔软的形状。没有特意寻找风景，抬头的时候刚好遇见。',
     'tpc_seed_city', NULL, 5, 3, NULL,
     UTC_TIMESTAMP(3) - INTERVAL 3 DAY, NULL, 1, 'seed-demo-cici-01',
     UTC_TIMESTAMP(3) - INTERVAL 3 DAY, UTC_TIMESTAMP(3) - INTERVAL 3 DAY),
    ('pst_demo_cici_02', 'usr_demo_cici', 'PUBLISHED',
     '成都的雨总是来得突然，躲进一家小店，喝完一杯热茶，雨也就刚好停了。',
     'tpc_seed_city', NULL, 3, 3, NULL,
     UTC_TIMESTAMP(3) - INTERVAL 12 HOUR, NULL, 1, 'seed-demo-cici-02',
     UTC_TIMESTAMP(3) - INTERVAL 12 HOUR, UTC_TIMESTAMP(3) - INTERVAL 12 HOUR),
    ('pst_demo_david_01', 'usr_demo_david', 'PUBLISHED',
     '最近在做一个记录灵感的小工具，先把最小版本跑起来，再慢慢把喜欢的细节加进去。',
     'tpc_seed_idea', NULL, 4, 3, NULL,
     UTC_TIMESTAMP(3) - INTERVAL 6 DAY, NULL, 1, 'seed-demo-david-01',
     UTC_TIMESTAMP(3) - INTERVAL 6 DAY, UTC_TIMESTAMP(3) - INTERVAL 6 DAY),
    ('pst_demo_david_02', 'usr_demo_david', 'PUBLISHED',
     '遇到问题时先写下假设，再用最小实验去验证。这个习惯让很多看似复杂的事情变得容易开始。',
     'tpc_seed_idea', NULL, 4, 3, NULL,
     UTC_TIMESTAMP(3) - INTERVAL 8 HOUR, NULL, 1, 'seed-demo-david-02',
     UTC_TIMESTAMP(3) - INTERVAL 8 HOUR, UTC_TIMESTAMP(3) - INTERVAL 8 HOUR),
    ('pst_demo_ella_01', 'usr_demo_ella', 'PUBLISHED',
     '读完一本书不代表结束，真正有意思的是某句话在很久以后重新出现在生活里。',
     'tpc_seed_reading', NULL, 5, 3, NULL,
     UTC_TIMESTAMP(3) - INTERVAL 7 DAY, NULL, 1, 'seed-demo-ella-01',
     UTC_TIMESTAMP(3) - INTERVAL 7 DAY, UTC_TIMESTAMP(3) - INTERVAL 7 DAY),
    ('pst_demo_ella_02', 'usr_demo_ella', 'PUBLISHED',
     '今天给自己留了半小时什么也不安排。发呆、散步和晒太阳，也都是生活的一部分。',
     'tpc_seed_daily', NULL, 3, 3, NULL,
     UTC_TIMESTAMP(3) - INTERVAL 3 HOUR, NULL, 1, 'seed-demo-ella-02',
     UTC_TIMESTAMP(3) - INTERVAL 3 HOUR, UTC_TIMESTAMP(3) - INTERVAL 3 HOUR);

-- ---------------------------------------------------------------------------
-- 3. 点赞：每条动态 3 个不同用户，共 30 条
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO post_likes (id, post_id, user_id, created_at) VALUES
    ('lik_demo_a01_b', 'pst_demo_alice_01', 'usr_demo_bob',   UTC_TIMESTAMP(3) - INTERVAL 4 DAY),
    ('lik_demo_a01_c', 'pst_demo_alice_01', 'usr_demo_cici',  UTC_TIMESTAMP(3) - INTERVAL 3 DAY),
    ('lik_demo_a01_d', 'pst_demo_alice_01', 'usr_demo_david', UTC_TIMESTAMP(3) - INTERVAL 2 DAY),
    ('lik_demo_a02_b', 'pst_demo_alice_02', 'usr_demo_bob',   UTC_TIMESTAMP(3) - INTERVAL 1 DAY),
    ('lik_demo_a02_e', 'pst_demo_alice_02', 'usr_demo_ella',  UTC_TIMESTAMP(3) - INTERVAL 20 HOUR),
    ('lik_demo_a02_d', 'pst_demo_alice_02', 'usr_demo_david', UTC_TIMESTAMP(3) - INTERVAL 18 HOUR),
    ('lik_demo_b01_a', 'pst_demo_bob_01',   'usr_demo_alice', UTC_TIMESTAMP(3) - INTERVAL 3 DAY),
    ('lik_demo_b01_c', 'pst_demo_bob_01',   'usr_demo_cici',  UTC_TIMESTAMP(3) - INTERVAL 2 DAY),
    ('lik_demo_b01_e', 'pst_demo_bob_01',   'usr_demo_ella',  UTC_TIMESTAMP(3) - INTERVAL 1 DAY),
    ('lik_demo_b02_a', 'pst_demo_bob_02',   'usr_demo_alice', UTC_TIMESTAMP(3) - INTERVAL 20 HOUR),
    ('lik_demo_b02_c', 'pst_demo_bob_02',   'usr_demo_cici',  UTC_TIMESTAMP(3) - INTERVAL 16 HOUR),
    ('lik_demo_b02_e', 'pst_demo_bob_02',   'usr_demo_ella',  UTC_TIMESTAMP(3) - INTERVAL 12 HOUR),
    ('lik_demo_c01_a', 'pst_demo_cici_01',  'usr_demo_alice', UTC_TIMESTAMP(3) - INTERVAL 2 DAY),
    ('lik_demo_c01_b', 'pst_demo_cici_01',  'usr_demo_bob',   UTC_TIMESTAMP(3) - INTERVAL 36 HOUR),
    ('lik_demo_c01_d', 'pst_demo_cici_01',  'usr_demo_david', UTC_TIMESTAMP(3) - INTERVAL 30 HOUR),
    ('lik_demo_c02_a', 'pst_demo_cici_02',  'usr_demo_alice', UTC_TIMESTAMP(3) - INTERVAL 10 HOUR),
    ('lik_demo_c02_d', 'pst_demo_cici_02',  'usr_demo_david', UTC_TIMESTAMP(3) - INTERVAL 8 HOUR),
    ('lik_demo_c02_e', 'pst_demo_cici_02',  'usr_demo_ella',  UTC_TIMESTAMP(3) - INTERVAL 6 HOUR),
    ('lik_demo_d01_a', 'pst_demo_david_01', 'usr_demo_alice', UTC_TIMESTAMP(3) - INTERVAL 5 DAY),
    ('lik_demo_d01_b', 'pst_demo_david_01', 'usr_demo_bob',   UTC_TIMESTAMP(3) - INTERVAL 4 DAY),
    ('lik_demo_d01_e', 'pst_demo_david_01', 'usr_demo_ella',  UTC_TIMESTAMP(3) - INTERVAL 3 DAY),
    ('lik_demo_d02_a', 'pst_demo_david_02', 'usr_demo_alice', UTC_TIMESTAMP(3) - INTERVAL 7 HOUR),
    ('lik_demo_d02_c', 'pst_demo_david_02', 'usr_demo_cici',  UTC_TIMESTAMP(3) - INTERVAL 6 HOUR),
    ('lik_demo_d02_e', 'pst_demo_david_02', 'usr_demo_ella',  UTC_TIMESTAMP(3) - INTERVAL 5 HOUR),
    ('lik_demo_e01_a', 'pst_demo_ella_01',  'usr_demo_alice', UTC_TIMESTAMP(3) - INTERVAL 6 DAY),
    ('lik_demo_e01_b', 'pst_demo_ella_01',  'usr_demo_bob',   UTC_TIMESTAMP(3) - INTERVAL 5 DAY),
    ('lik_demo_e01_c', 'pst_demo_ella_01',  'usr_demo_cici',  UTC_TIMESTAMP(3) - INTERVAL 4 DAY),
    ('lik_demo_e02_b', 'pst_demo_ella_02',  'usr_demo_bob',   UTC_TIMESTAMP(3) - INTERVAL 2 HOUR),
    ('lik_demo_e02_c', 'pst_demo_ella_02',  'usr_demo_cici',  UTC_TIMESTAMP(3) - INTERVAL 90 MINUTE),
    ('lik_demo_e02_d', 'pst_demo_ella_02',  'usr_demo_david', UTC_TIMESTAMP(3) - INTERVAL 60 MINUTE);

-- ---------------------------------------------------------------------------
-- 4. 收藏：每条动态 2 个不同用户，共 20 条
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO post_bookmarks (id, post_id, user_id, bookmarked_at) VALUES
    ('bm_demo_a01_b', 'pst_demo_alice_01', 'usr_demo_bob',   UTC_TIMESTAMP(3) - INTERVAL 3 DAY),
    ('bm_demo_a01_e', 'pst_demo_alice_01', 'usr_demo_ella',  UTC_TIMESTAMP(3) - INTERVAL 2 DAY),
    ('bm_demo_a02_c', 'pst_demo_alice_02', 'usr_demo_cici',  UTC_TIMESTAMP(3) - INTERVAL 18 HOUR),
    ('bm_demo_a02_d', 'pst_demo_alice_02', 'usr_demo_david', UTC_TIMESTAMP(3) - INTERVAL 16 HOUR),
    ('bm_demo_b01_a', 'pst_demo_bob_01',   'usr_demo_alice', UTC_TIMESTAMP(3) - INTERVAL 3 DAY),
    ('bm_demo_b01_e', 'pst_demo_bob_01',   'usr_demo_ella',  UTC_TIMESTAMP(3) - INTERVAL 2 DAY),
    ('bm_demo_b02_a', 'pst_demo_bob_02',   'usr_demo_alice', UTC_TIMESTAMP(3) - INTERVAL 12 HOUR),
    ('bm_demo_b02_c', 'pst_demo_bob_02',   'usr_demo_cici',  UTC_TIMESTAMP(3) - INTERVAL 10 HOUR),
    ('bm_demo_c01_b', 'pst_demo_cici_01',  'usr_demo_bob',   UTC_TIMESTAMP(3) - INTERVAL 30 HOUR),
    ('bm_demo_c01_d', 'pst_demo_cici_01',  'usr_demo_david', UTC_TIMESTAMP(3) - INTERVAL 24 HOUR),
    ('bm_demo_c02_a', 'pst_demo_cici_02',  'usr_demo_alice', UTC_TIMESTAMP(3) - INTERVAL 8 HOUR),
    ('bm_demo_c02_e', 'pst_demo_cici_02',  'usr_demo_ella',  UTC_TIMESTAMP(3) - INTERVAL 7 HOUR),
    ('bm_demo_d01_a', 'pst_demo_david_01', 'usr_demo_alice', UTC_TIMESTAMP(3) - INTERVAL 4 DAY),
    ('bm_demo_d01_c', 'pst_demo_david_01', 'usr_demo_cici',  UTC_TIMESTAMP(3) - INTERVAL 3 DAY),
    ('bm_demo_d02_b', 'pst_demo_david_02', 'usr_demo_bob',   UTC_TIMESTAMP(3) - INTERVAL 6 HOUR),
    ('bm_demo_d02_e', 'pst_demo_david_02', 'usr_demo_ella',  UTC_TIMESTAMP(3) - INTERVAL 5 HOUR),
    ('bm_demo_e01_b', 'pst_demo_ella_01',  'usr_demo_bob',   UTC_TIMESTAMP(3) - INTERVAL 5 DAY),
    ('bm_demo_e01_d', 'pst_demo_ella_01',  'usr_demo_david', UTC_TIMESTAMP(3) - INTERVAL 4 DAY),
    ('bm_demo_e02_a', 'pst_demo_ella_02',  'usr_demo_alice', UTC_TIMESTAMP(3) - INTERVAL 2 HOUR),
    ('bm_demo_e02_c', 'pst_demo_ella_02',  'usr_demo_cici',  UTC_TIMESTAMP(3) - INTERVAL 90 MINUTE);

-- ---------------------------------------------------------------------------
-- 5. 评论和回复：每条动态 2 条一级评论 + 1 条回复，共 30 条
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO comments
    (id, post_id, author_id, parent_id, content, status, created_at, updated_at)
VALUES
    ('cmt_demo_a01_1', 'pst_demo_alice_01', 'usr_demo_bob', NULL, '整理之后的空间真的会让人更想开始。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 4 DAY, UTC_TIMESTAMP(3) - INTERVAL 4 DAY),
    ('cmt_demo_a01_2', 'pst_demo_alice_01', 'usr_demo_ella', NULL, '我也需要给自己的桌面做一次减法。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 3 DAY, UTC_TIMESTAMP(3) - INTERVAL 3 DAY),
    ('cmt_demo_a01_r1', 'pst_demo_alice_01', 'usr_demo_alice', 'cmt_demo_a01_1', '一起加油，先从收起不用的东西开始。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 3 DAY, UTC_TIMESTAMP(3) - INTERVAL 3 DAY),
    ('cmt_demo_a02_1', 'pst_demo_alice_02', 'usr_demo_cici', NULL, '这个配色很舒服，橙色作为点缀刚刚好。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 20 HOUR, UTC_TIMESTAMP(3) - INTERVAL 20 HOUR),
    ('cmt_demo_a02_2', 'pst_demo_alice_02', 'usr_demo_david', NULL, '收藏了，之后做页面时可以参考。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 18 HOUR, UTC_TIMESTAMP(3) - INTERVAL 18 HOUR),
    ('cmt_demo_a02_r1', 'pst_demo_alice_02', 'usr_demo_alice', 'cmt_demo_a02_1', '谢谢，最近也在尝试更多低饱和配色。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 17 HOUR, UTC_TIMESTAMP(3) - INTERVAL 17 HOUR),
    ('cmt_demo_b01_1', 'pst_demo_bob_01', 'usr_demo_alice', NULL, '可复用组件确实能省下很多重复沟通的时间。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 3 DAY, UTC_TIMESTAMP(3) - INTERVAL 3 DAY),
    ('cmt_demo_b01_2', 'pst_demo_bob_01', 'usr_demo_cici', NULL, '从零到一的过程很有成就感。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 2 DAY, UTC_TIMESTAMP(3) - INTERVAL 2 DAY),
    ('cmt_demo_b01_r1', 'pst_demo_bob_01', 'usr_demo_bob', 'cmt_demo_b01_1', '是的，后面维护起来也轻松很多。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 2 DAY, UTC_TIMESTAMP(3) - INTERVAL 2 DAY),
    ('cmt_demo_b02_1', 'pst_demo_bob_02', 'usr_demo_ella', NULL, '这个想法很适合放在办公桌旁边。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 18 HOUR, UTC_TIMESTAMP(3) - INTERVAL 18 HOUR),
    ('cmt_demo_b02_2', 'pst_demo_bob_02', 'usr_demo_alice', NULL, '提醒专注，也提醒休息，这个平衡很重要。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 16 HOUR, UTC_TIMESTAMP(3) - INTERVAL 16 HOUR),
    ('cmt_demo_b02_r1', 'pst_demo_bob_02', 'usr_demo_bob', 'cmt_demo_b02_2', '正在努力把休息也安排进计划里。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 14 HOUR, UTC_TIMESTAMP(3) - INTERVAL 14 HOUR),
    ('cmt_demo_c01_1', 'pst_demo_cici_01', 'usr_demo_david', NULL, '这种偶然遇见的风景最让人惊喜。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 2 DAY, UTC_TIMESTAMP(3) - INTERVAL 2 DAY),
    ('cmt_demo_c01_2', 'pst_demo_cici_01', 'usr_demo_ella', NULL, '云的形状看起来像一幅画。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 36 HOUR, UTC_TIMESTAMP(3) - INTERVAL 36 HOUR),
    ('cmt_demo_c01_r1', 'pst_demo_cici_01', 'usr_demo_cici', 'cmt_demo_c01_1', '所以忍不住停下来拍了几张。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 30 HOUR, UTC_TIMESTAMP(3) - INTERVAL 30 HOUR),
    ('cmt_demo_c02_1', 'pst_demo_cici_02', 'usr_demo_alice', NULL, '下雨天躲进小店也很有电影感。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 9 HOUR, UTC_TIMESTAMP(3) - INTERVAL 9 HOUR),
    ('cmt_demo_c02_2', 'pst_demo_cici_02', 'usr_demo_david', NULL, '热茶和雨声，听起来就很放松。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 8 HOUR, UTC_TIMESTAMP(3) - INTERVAL 8 HOUR),
    ('cmt_demo_c02_r1', 'pst_demo_cici_02', 'usr_demo_cici', 'cmt_demo_c02_2', '那天刚好坐到了窗边的位置。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 7 HOUR, UTC_TIMESTAMP(3) - INTERVAL 7 HOUR),
    ('cmt_demo_d01_1', 'pst_demo_david_01', 'usr_demo_bob', NULL, '先做最小版本这个思路很实用。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 5 DAY, UTC_TIMESTAMP(3) - INTERVAL 5 DAY),
    ('cmt_demo_d01_2', 'pst_demo_david_01', 'usr_demo_ella', NULL, '期待看到后续更新。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 4 DAY, UTC_TIMESTAMP(3) - INTERVAL 4 DAY),
    ('cmt_demo_d01_r1', 'pst_demo_david_01', 'usr_demo_david', 'cmt_demo_d01_2', '有新进展会继续分享。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 3 DAY, UTC_TIMESTAMP(3) - INTERVAL 3 DAY),
    ('cmt_demo_d02_1', 'pst_demo_david_02', 'usr_demo_alice', NULL, '把问题拆小之后确实容易很多。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 7 HOUR, UTC_TIMESTAMP(3) - INTERVAL 7 HOUR),
    ('cmt_demo_d02_2', 'pst_demo_david_02', 'usr_demo_cici', NULL, '最小实验是个很好的习惯。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 6 HOUR, UTC_TIMESTAMP(3) - INTERVAL 6 HOUR),
    ('cmt_demo_d02_r1', 'pst_demo_david_02', 'usr_demo_david', 'cmt_demo_d02_1', '先验证方向，再投入时间，确实安心很多。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 5 HOUR, UTC_TIMESTAMP(3) - INTERVAL 5 HOUR),
    ('cmt_demo_e01_1', 'pst_demo_ella_01', 'usr_demo_bob', NULL, '这句话很有共鸣，书里的内容常常会在生活里回响。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 6 DAY, UTC_TIMESTAMP(3) - INTERVAL 6 DAY),
    ('cmt_demo_e01_2', 'pst_demo_ella_01', 'usr_demo_cici', NULL, '最近也遇到了一句很久之后才读懂的话。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 5 DAY, UTC_TIMESTAMP(3) - INTERVAL 5 DAY),
    ('cmt_demo_e01_r1', 'pst_demo_ella_01', 'usr_demo_ella', 'cmt_demo_e01_1', '也许这就是阅读最有趣的地方。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 4 DAY, UTC_TIMESTAMP(3) - INTERVAL 4 DAY),
    ('cmt_demo_e02_1', 'pst_demo_ella_02', 'usr_demo_alice', NULL, '给自己留白真的很重要。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 2 HOUR, UTC_TIMESTAMP(3) - INTERVAL 2 HOUR),
    ('cmt_demo_e02_2', 'pst_demo_ella_02', 'usr_demo_david', NULL, '散步和晒太阳是很好的充电方式。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 90 MINUTE, UTC_TIMESTAMP(3) - INTERVAL 90 MINUTE),
    ('cmt_demo_e02_r1', 'pst_demo_ella_02', 'usr_demo_ella', 'cmt_demo_e02_1', '今天的电量已经恢复不少了。', 'VISIBLE', UTC_TIMESTAMP(3) - INTERVAL 60 MINUTE, UTC_TIMESTAMP(3) - INTERVAL 60 MINUTE);

-- ---------------------------------------------------------------------------
-- 6. 分享事件：每条动态 2 条，共 20 条
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO post_share_events
    (id, post_id, user_id, channel, created_at)
VALUES
    ('shr_demo_a01_b', 'pst_demo_alice_01', 'usr_demo_bob',   'WECHAT', UTC_TIMESTAMP(3) - INTERVAL 3 DAY),
    ('shr_demo_a01_e', 'pst_demo_alice_01', 'usr_demo_ella',  'QQ',     UTC_TIMESTAMP(3) - INTERVAL 2 DAY),
    ('shr_demo_a02_c', 'pst_demo_alice_02', 'usr_demo_cici',  'WECHAT', UTC_TIMESTAMP(3) - INTERVAL 18 HOUR),
    ('shr_demo_a02_d', 'pst_demo_alice_02', 'usr_demo_david', 'DOUYIN', UTC_TIMESTAMP(3) - INTERVAL 16 HOUR),
    ('shr_demo_b01_a', 'pst_demo_bob_01',   'usr_demo_alice', 'WECHAT', UTC_TIMESTAMP(3) - INTERVAL 3 DAY),
    ('shr_demo_b01_e', 'pst_demo_bob_01',   'usr_demo_ella',  'QQ',     UTC_TIMESTAMP(3) - INTERVAL 2 DAY),
    ('shr_demo_b02_a', 'pst_demo_bob_02',   'usr_demo_alice', 'WECHAT', UTC_TIMESTAMP(3) - INTERVAL 12 HOUR),
    ('shr_demo_b02_c', 'pst_demo_bob_02',   'usr_demo_cici',  'DOUYIN', UTC_TIMESTAMP(3) - INTERVAL 10 HOUR),
    ('shr_demo_c01_b', 'pst_demo_cici_01',  'usr_demo_bob',   'QQ',     UTC_TIMESTAMP(3) - INTERVAL 30 HOUR),
    ('shr_demo_c01_d', 'pst_demo_cici_01',  'usr_demo_david', 'WECHAT', UTC_TIMESTAMP(3) - INTERVAL 24 HOUR),
    ('shr_demo_c02_a', 'pst_demo_cici_02',  'usr_demo_alice', 'WECHAT', UTC_TIMESTAMP(3) - INTERVAL 8 HOUR),
    ('shr_demo_c02_e', 'pst_demo_cici_02',  'usr_demo_ella',  'QQ',     UTC_TIMESTAMP(3) - INTERVAL 7 HOUR),
    ('shr_demo_d01_a', 'pst_demo_david_01', 'usr_demo_alice', 'DOUYIN', UTC_TIMESTAMP(3) - INTERVAL 4 DAY),
    ('shr_demo_d01_c', 'pst_demo_david_01', 'usr_demo_cici',  'WECHAT', UTC_TIMESTAMP(3) - INTERVAL 3 DAY),
    ('shr_demo_d02_b', 'pst_demo_david_02', 'usr_demo_bob',   'WECHAT', UTC_TIMESTAMP(3) - INTERVAL 6 HOUR),
    ('shr_demo_d02_e', 'pst_demo_david_02', 'usr_demo_ella',  'QQ',     UTC_TIMESTAMP(3) - INTERVAL 5 HOUR),
    ('shr_demo_e01_b', 'pst_demo_ella_01',  'usr_demo_bob',   'WECHAT', UTC_TIMESTAMP(3) - INTERVAL 5 DAY),
    ('shr_demo_e01_d', 'pst_demo_ella_01',  'usr_demo_david', 'DOUYIN', UTC_TIMESTAMP(3) - INTERVAL 4 DAY),
    ('shr_demo_e02_a', 'pst_demo_ella_02',  'usr_demo_alice', 'WECHAT', UTC_TIMESTAMP(3) - INTERVAL 2 HOUR),
    ('shr_demo_e02_c', 'pst_demo_ella_02',  'usr_demo_cici',  'QQ',     UTC_TIMESTAMP(3) - INTERVAL 90 MINUTE);

-- 按互动明细重算计数，保证重复执行或已有部分数据时仍保持一致。
UPDATE posts p
LEFT JOIN (
    SELECT post_id, COUNT(*) AS cnt
    FROM post_likes
    GROUP BY post_id
) l ON l.post_id = p.id
LEFT JOIN (
    SELECT post_id, COUNT(*) AS cnt
    FROM comments
    WHERE status = 'VISIBLE' AND deleted_at IS NULL
    GROUP BY post_id
) c ON c.post_id = p.id
SET p.like_count = COALESCE(l.cnt, 0),
    p.comment_count = COALESCE(c.cnt, 0)
WHERE p.id LIKE 'pst_demo_%';

-- 同步话题发布数，按演示动态实际数量重算，重复执行不会累加。
UPDATE topics t
LEFT JOIN (
    SELECT topic_id, COUNT(*) AS published_count
    FROM posts
    WHERE status = 'PUBLISHED' AND deleted_at IS NULL
    GROUP BY topic_id
) p ON p.topic_id = t.id
SET t.post_count = COALESCE(p.published_count, 0)
WHERE t.id IN (
    'tpc_seed_daily', 'tpc_seed_tech', 'tpc_seed_design',
    'tpc_seed_reading', 'tpc_seed_city', 'tpc_seed_idea'
);
