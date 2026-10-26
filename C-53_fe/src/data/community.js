// 社区页面演示数据：成员、动态和私信样例，不作为真实接口数据源。
export const peopleFixtures = [
  { id: 1, name: '沈闻', handle: '@shenwen', initials: '沈', field: '独立摄影师', followed: false, tone: 'violet' },
  { id: 2, name: '阿屿', handle: '@a-yu', initials: '屿', field: '交互设计师', followed: false, tone: 'coral' },
  { id: 3, name: '陈默', handle: '@chenmo', initials: '默', field: '前端工程师', followed: true, tone: 'green' },
]

export const followersFixtures = [
  { id: 11, name: '林澈', handle: '@linche', initials: '林', field: '产品设计师', note: '共同关注 18 人', followed: true, followsYou: true, tone: 'blue' },
  { id: 12, name: '沈闻', handle: '@shenwen', initials: '沈', field: '独立摄影师', note: '关注你 8 个月', followed: true, followsYou: true, tone: 'violet' },
  { id: 13, name: '阿屿', handle: '@a-yu', initials: '屿', field: '交互设计师', note: '共同关注 9 人', followed: true, followsYou: true, tone: 'coral' },
  { id: 14, name: '陈默', handle: '@chenmo', initials: '默', field: '前端工程师', note: '关注你 3 个月', followed: true, followsYou: true, tone: 'green' },
  { id: 15, name: '周野', handle: '@zhouye', initials: '野', field: '独立写作者', note: '关注你 2 周', followed: false, followsYou: true, tone: 'gold' },
]

export const followingFixtures = [
  { id: 21, name: '陈默', handle: '@chenmo', initials: '默', field: '前端工程师', note: '最近发布于昨天', followed: true, followsYou: true, tone: 'green' },
  { id: 22, name: '阿屿', handle: '@a-yu', initials: '屿', field: '交互设计师', note: '最近发布于 2 天前', followed: true, followsYou: true, tone: 'coral' },
  { id: 23, name: '纪川', handle: '@jichuan', initials: '纪', field: '产品研究者', note: '共同关注 22 人', followed: true, followsYou: false, tone: 'blue' },
  { id: 24, name: '沈闻', handle: '@shenwen', initials: '沈', field: '独立摄影师', note: '最近发布于 4 天前', followed: true, followsYou: true, tone: 'violet' },
  { id: 25, name: '宋遥', handle: '@songyao', initials: '遥', field: '空间设计师', note: '共同关注 11 人', followed: true, followsYou: false, tone: 'gold' },
]

export const postsFixtures = [
  {
    id: 1, author: '林澈', handle: '@linche', initials: '林', role: '产品设计师', time: '12 分钟前', tone: 'blue',
    topic: '产品手记', title: '当社区不再追求“无限滚动”',
    body: '最近在重新思考内容社区的节奏：不是让人停不下来，而是让每一次停留都更有意义。我们把“读完”设计成一个明确的状态，也给讨论留出了真正的空间。',
    detail: [
      '过去很长一段时间，内容产品都在用更短的反馈周期争夺注意力。刷新、滚动、下一个推荐，每一步都被设计得几乎没有停顿。但当所有体验都指向“继续”，人也很难判断自己究竟收获了什么。',
      '我们尝试换一种思路：把一次阅读看成一段完整的关系。读者可以明确地标记读完、留下回应，或者把尚未想清楚的问题带走。推荐不再只看停留时长，也开始尊重主动结束。',
      '真正有生命力的社区，不应该只有源源不断的内容，还应该容得下思考之后的沉默，以及从沉默里长出来的新讨论。',
    ],
    note: '正在共创：更温和的内容推荐机制', likes: 128, liked: false, saved: false,
    comments: [
      { id: 1, author: '陈默', initials: '默', text: '很喜欢“读完是一个状态”这个想法。结束感本身也是体验的一部分。' },
      { id: 2, author: '沈闻', initials: '沈', text: '也许可以增加一张今日阅读完成卡片，让节奏被看见。' },
    ],
    commentsOpen: false, reply: '',
  },
  {
    id: 2, author: '沈闻', handle: '@shenwen', initials: '沈', role: '独立摄影师', time: '1 小时前', tone: 'violet',
    topic: '城市观察', title: '清晨六点，城市把声音调低了',
    body: '没有照片也没关系。桥下刚开门的早餐铺、第一班公交车、扫过墙面的斜光，这些细节本身就是一组完整的叙事。',
    detail: [
      '我沿着河边慢慢走，城市还没有完全醒来。早餐铺的蒸汽越过玻璃，第一班公交车停在空荡的站台，清洁车在远处留下规律的声音。',
      '平时很难注意到的东西，在这个时刻都有了清楚的轮廓。光线并不强，却恰好让每一处日常显得认真。',
      '后来我没有举起相机。记住一个清晨，有时不一定需要照片；只要愿意停下来，它就已经被完整地看见了。',
    ],
    note: '观察记录 · 杭州', likes: 86, liked: true, saved: true,
    comments: [{ id: 1, author: '阿屿', initials: '屿', text: '读完脑海里已经有画面了。文字也可以是镜头。' }],
    commentsOpen: false, reply: '',
  },
  {
    id: 3, author: '陈默', handle: '@chenmo', initials: '默', role: '前端工程师', time: '昨天', tone: 'green',
    topic: '独立创作', title: '这周做了一个很小，但很有用的决定',
    body: '把所有项目的“下一步”都改成一个能在 20 分钟内完成的动作。没有宏大计划，只留下足够具体的开始。结果是卡了两周的东西终于动起来了。',
    detail: [
      '以前的任务列表里总是写着“完成网站”“整理作品集”或者“开始写作”。它们看起来明确，真正坐下来时却不知道第一步应该做什么。',
      '现在我只写可以立刻执行的动作：确定首页的一句话、选出三张作品、写下开头的一百字。二十分钟不是限制，而是帮助自己越过启动阻力。',
      '进展往往不是来自更强的意志力，而是来自一个足够小、足够清楚的入口。',
    ],
    note: '本周复盘 · #持续创作', likes: 214, liked: false, saved: false, comments: [], commentsOpen: false, reply: '',
  },
]

export const profileArchivePostsFixtures = [
  {
    id: 101, author: '许知远', handle: '@xuzhiyuan', initials: '许', role: '空间发起人', time: '3 天前', tone: 'ink',
    topic: '独立创作', title: '给长期创作留一块不被打扰的地方',
    body: '我想要的不是更快地发布，而是让每一个尚未成熟的想法，都有机会被认真地保存、讨论和继续完成。',
    detail: [
      '创作最难的部分，往往不是产生想法，而是为它保留足够长的时间。很多念头刚刚出现，就被下一条消息、下一项任务覆盖了。',
      '所以我开始整理一个更安静的空间。这里不要求每一次表达都完整，也不急着给出结论。草稿、讨论和未完成的尝试，都可以成为共同创作的起点。',
      '当一个空间愿意保护过程，作品才有机会慢慢长出真正属于自己的样子。',
    ],
    note: '空间手记 · #持续创作', likes: 72, liked: false, saved: true,
    comments: [{ id: 1011, author: '林澈', initials: '林', text: '保护过程这件事很重要，期待这个空间继续长大。', replies: [] }],
    commentsOpen: false, reply: '', date: '09.18', year: '2026',
  },
  {
    id: 102, author: '许知远', handle: '@xuzhiyuan', initials: '许', role: '空间发起人', time: '1 周前', tone: 'ink',
    topic: '产品手记', title: '一次关于“共同完成”的小实验',
    body: '把一个人的草稿开放给五位朋友，在三天里只做补充、不做否定。最后得到的不是折中方案，而是一个谁都没有单独想到的答案。',
    detail: [
      '这次实验只设了一条规则：每个人都可以增加内容，但暂时不要删除别人的部分。我们想看看，当判断被延后，想法会发生什么。',
      '最有趣的变化是，大家不再急着证明自己正确，而是开始寻找上一位参与者留下的线索。讨论的重心从立场，慢慢转向了作品本身。',
      '共同完成并不意味着消除差异。恰恰相反，它让差异变成了可以继续生长的材料。',
    ],
    note: '共创实验 · 5 位参与者', likes: 118, liked: true, saved: false,
    comments: [
      { id: 1021, author: '阿屿', initials: '屿', text: '“只做补充、不做否定”这个规则很有启发。', replies: [] },
      { id: 1022, author: '陈默', initials: '默', text: '想试试把这个方法用在下一次产品评审里。', replies: [] },
    ],
    commentsOpen: false, reply: '', date: '09.12', year: '2026',
  },
  {
    id: 103, author: '许知远', handle: '@xuzhiyuan', initials: '许', role: '空间发起人', time: '2 周前', tone: 'ink',
    topic: '城市观察', title: '傍晚之后，工作室才真正安静下来',
    body: '窗外的灯一盏一盏亮起来，白天没有想明白的问题，反而在这个时候出现了更清楚的轮廓。',
    detail: [
      '傍晚之后，工作室里的声音会逐渐减少。键盘声、走动声和街道上的车辆，都像被更柔和的空气包住了。',
      '我很喜欢在这个时候重新打开白天卡住的文档。不是为了加班，而是想看看，当紧迫感消失以后，问题会不会呈现出不同的形状。',
      '有些答案不需要更用力地寻找，只需要等周围安静一点。',
    ],
    note: '工作室记录 · 周五', likes: 96, liked: false, saved: false,
    comments: [], commentsOpen: false, reply: '', date: '09.05', year: '2026',
  },
]

export const conversationsFixtures = [
  { id: 1, name: '阿屿', initials: '屿', tone: 'coral', preview: '我把新版流程整理好了', time: '10:24', unread: 2 },
  { id: 2, name: '陈默', initials: '默', tone: 'green', preview: '周四晚上可以', time: '昨天', unread: 0 },
  { id: 3, name: '共创小组', initials: '创', tone: 'blue', preview: '沈闻：刚上传了讨论稿', time: '周一', unread: 1 },
]

export const chatMessagesFixtures = [
  { id: 1, mine: false, text: '我看完你昨天发的社区节奏方案了，方向很棒。' },
  { id: 2, mine: false, text: '我把新版流程整理好了，想听听你的看法。' },
  { id: 3, mine: true, text: '好，我下午仔细看一下。我们可以把“讨论”再往前放一点。' },
]
