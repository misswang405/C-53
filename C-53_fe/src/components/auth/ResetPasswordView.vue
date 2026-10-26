<script setup>
// 密码重置页面：收集联系方式、验证码和新密码。
// 密码重置表单及验证码倒计时由父组件管理。
defineProps({
  form: { type: Object, required: true },
  error: { type: String, default: '' },
  passwordVisible: { type: Boolean, default: false },
  confirmPasswordVisible: { type: Boolean, default: false },
  codeCountdown: { type: Number, default: 0 },
  codeSending: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  maskedTarget: { type: String, default: '' },
})

// 将用户操作与双向绑定变更通知父组件，由父组件统一处理业务状态。
defineEmits([
  'submit',
  'back-home',
  'back-login',
  'clear-error',
  'change-identifier-type',
  'send-code',
  'toggle-password',
  'toggle-confirm-password',
])
</script>

<template>
  <main class="login-page">
    <section class="login-shell register-shell">
      <div class="login-story register-story">
        <button class="brand login-brand" type="button" @click="$emit('back-home')">
          <span class="brand-orbit"><i></i></span><span>C—53</span>
        </button>
        <div>
          <p class="kicker">RECOVER ACCOUNT</p>
          <h1>找回通行方式，<br />回到创作现场。</h1>
          <p>验证注册时使用的手机号或邮箱，并为账号设置一个新的安全密码。</p>
        </div>
        <div class="login-community">
          <p><strong>安全验证</strong><br />请尽快使用验证码，请勿转发给他人</p>
        </div>
      </div>

      <form class="login-form register-form" @submit.prevent="$emit('submit')">
        <header><p class="kicker">RESET PASSWORD</p><h2>重置密码</h2><p>完成身份验证后设置新密码</p></header>

        <div class="login-mode-switch identifier-switch" role="tablist" aria-label="找回方式">
          <button type="button" :disabled="loading" role="tab" :aria-selected="form.identifierType === 'PHONE'" :class="{ active: form.identifierType === 'PHONE' }" @click="$emit('change-identifier-type', 'PHONE')">手机号</button>
          <button type="button" :disabled="loading" role="tab" :aria-selected="form.identifierType === 'EMAIL'" :class="{ active: form.identifierType === 'EMAIL' }" @click="$emit('change-identifier-type', 'EMAIL')">邮箱</button>
        </div>

        <label>
          <span>{{ form.identifierType === 'PHONE' ? '手机号' : '邮箱' }}</span>
          <input
            v-model="form.identifier"
            :disabled="loading"
            :type="form.identifierType === 'PHONE' ? 'tel' : 'email'"
            :autocomplete="form.identifierType === 'PHONE' ? 'tel' : 'email'"
            :inputmode="form.identifierType === 'PHONE' ? 'tel' : 'email'"
            :placeholder="form.identifierType === 'PHONE' ? '请输入中国大陆手机号' : '请输入邮箱地址'"
            @input="$emit('clear-error')"
          />
        </label>

        <label>
          <span>验证码</span>
          <div class="code-field">
            <input
              v-model="form.code"
              type="text"
              inputmode="numeric"
              autocomplete="one-time-code"
              maxlength="6"
              pattern="[0-9]*"
              placeholder="请输入 6 位验证码"
              @input="form.code = form.code.replace(/\D/g, '').slice(0, 6); $emit('clear-error')"
            />
            <button type="button" :disabled="codeSending || loading || codeCountdown > 0" @click="$emit('send-code')">
              {{ codeSending ? '发送中…' : codeCountdown > 0 ? `${codeCountdown}s 后重发` : '获取验证码' }}
            </button>
          </div>
          <small v-if="maskedTarget" class="code-hint">验证码已发送至 {{ maskedTarget }}</small>
        </label>

        <label>
          <span>新密码</span>
          <div class="password-field">
            <input v-model="form.newPassword" :type="passwordVisible ? 'text' : 'password'" autocomplete="new-password" placeholder="至少 8 位，包含字母和数字" @input="$emit('clear-error')" />
            <button type="button" @click="$emit('toggle-password')">{{ passwordVisible ? '隐藏' : '显示' }}</button>
          </div>
        </label>

        <label>
          <span>确认新密码</span>
          <div class="password-field">
            <input v-model="form.confirmPassword" :type="confirmPasswordVisible ? 'text' : 'password'" autocomplete="new-password" placeholder="再次输入新密码" @input="$emit('clear-error')" />
            <button type="button" @click="$emit('toggle-confirm-password')">{{ confirmPasswordVisible ? '隐藏' : '显示' }}</button>
          </div>
        </label>

        <p v-if="error" class="login-error">{{ error }}</p>
        <button class="login-submit" type="submit" :disabled="loading || codeSending">{{ loading ? '提交中…' : '重置密码' }}</button>
        <p class="login-register">想起密码了？<button type="button" @click="$emit('back-login')">返回登录</button></p>
      </form>
    </section>
  </main>
</template>
