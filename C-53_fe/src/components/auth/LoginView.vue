<script setup>
// 登录页面：展示验证码、密码登录及已保存账号入口。
// 父组件提供登录表单、验证码倒计时与账号列表；本组件仅负责输入和展示。
defineProps({
  form: { type: Object, required: true },
  error: { type: String, default: '' },
  passwordVisible: { type: Boolean, default: false },
  isCodeLogin: { type: Boolean, default: true },
  codeCountdown: { type: Number, default: 0 },
  codeSending: { type: Boolean, default: false },
  maskedTarget: { type: String, default: '' },
  accounts: { type: Array, required: true },
})

// 将用户操作与双向绑定变更通知父组件，由父组件统一处理业务状态。
defineEmits(['submit', 'back', 'clear-error', 'change-mode', 'toggle-password', 'send-code', 'forgot', 'quick-login', 'register'])
</script>

<template>
  <main class="login-page">
    <section class="login-shell">
      <div class="login-story">
        <button class="brand login-brand" type="button" @click="$emit('back')">
          <span class="brand-orbit"><i></i></span><span>C-53</span>
        </button>
        <div>
          <p class="kicker">WELCOME BACK</p>
          <h1>重新进入<br />共同创作现场。</h1>
          <p>登录后继续发布想法、参与讨论，也和你在这里认识的人保持联系。</p>
        </div>
        <div class="login-community">
          <span class="avatar mini violet">沈</span><span class="avatar mini coral">屿</span><span
            class="avatar mini green">默</span>
          <p><strong>28 位成员在线</strong><br />新的讨论正在发生</p>
        </div>
      </div>

      <form class="login-form" @submit.prevent="$emit('submit')">
        <header>
          <p class="kicker">ACCOUNT</p>
          <h2>登录 C—53</h2>
          <p>{{ isCodeLogin ? '使用手机号或邮箱验证码继续' : '使用手机号或邮箱和密码继续' }}</p>
        </header>
        <div class="login-mode-switch" role="tablist" aria-label="登录方式">
          <button type="button" role="tab" :aria-selected="isCodeLogin" :class="{ active: isCodeLogin }"
            @click="$emit('change-mode', 'code')">验证码登录</button>
          <button type="button" role="tab" :aria-selected="!isCodeLogin" :class="{ active: !isCodeLogin }"
            @click="$emit('change-mode', 'password')">密码登录</button>
        </div>
        <label>
          <span>{{ isCodeLogin ? '手机号或邮箱' : '手机号、邮箱或用户名' }}</span>
          <input v-model="form.account" type="text" autocomplete="username" inputmode="text"
            :placeholder="isCodeLogin ? '手机号 / 邮箱' : '手机号 / 邮箱 / 用户名'" @input="$emit('clear-error')" />
        </label>
        <label v-if="isCodeLogin">
          <span>验证码</span>
          <div class="code-field">
            <input v-model="form.code" type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="6"
              pattern="[0-9]*" placeholder="请输入 6 位验证码" aria-label="验证码"
              @input="form.code = form.code.replace(/\D/g, '').slice(0, 6); $emit('clear-error')" />
            <button type="button" :disabled="codeSending || codeCountdown > 0" @click="$emit('send-code')">
              {{ codeSending ? '发送中…' : codeCountdown > 0 ? `${codeCountdown}s 后重发` : '获取验证码' }}
            </button>
          </div>
          <small v-if="maskedTarget" class="code-hint">验证码已发送至 {{ maskedTarget }}</small>
        </label>
        <label v-else>
          <span>密码</span>
          <div class="password-field">
            <input v-model="form.password" :type="passwordVisible ? 'text' : 'password'" autocomplete="current-password"
              placeholder="请输入密码" @input="$emit('clear-error')" />
            <button type="button" @click="$emit('toggle-password')">{{ passwordVisible ? '隐藏' : '显示' }}</button>
          </div>
        </label>
        <div class="login-options">
          <label><input v-model="form.remember" type="checkbox" /><span></span>保持登录</label>
          <button v-if="!isCodeLogin" type="button" @click="$emit('forgot')">忘记密码？</button>
          <small v-else>验证码 5 分钟内有效</small>
        </div>
        <p v-if="error" class="login-error">{{ error }}</p>
        <button class="login-submit" type="submit">{{ isCodeLogin ? '验证码登录' : '密码登录' }}</button>
        <div class="login-divider"><span>或</span></div>
        <div class="quick-accounts">
          <p>快速切换已保存账号</p>
          <button v-for="account in accounts" :key="account.id" type="button" @click="$emit('quick-login', account)">
            <span class="avatar mini" :class="account.tone">{{ account.initials }}</span>
            <span><strong>{{ account.nickname }}</strong><small>@{{ account.username }}</small></span><b>→</b>
          </button>
        </div>
        <p class="login-register">第一次来到这里？<button type="button" @click="$emit('register')">创建账号</button></p>
      </form>
    </section>
  </main>
</template>
