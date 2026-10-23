<script setup>
defineProps({
  form: { type: Object, required: true },
  error: { type: String, default: '' },
  passwordVisible: { type: Boolean, default: false },
  confirmPasswordVisible: { type: Boolean, default: false },
  codeCountdown: { type: Number, default: 0 },
  codeSending: { type: Boolean, default: false },
  maskedTarget: { type: String, default: '' },
})

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
          <p class="kicker">JOIN THE COMMUNITY</p>
          <h1>带着一个想法，<br />来到共同创作现场。</h1>
          <p>建立你的创作者身份，发布正在发生的事，也从一条认真回应开始认识彼此。</p>
        </div>
        <div class="login-community">
          <p><strong>加入开放社区</strong><br />注册后可以发布、收藏和参与讨论</p>
        </div>
      </div>

      <form class="login-form register-form" @submit.prevent="$emit('submit')">
        <header><p class="kicker">CREATE ACCOUNT</p><h2>创建 C—53 账号</h2><p>验证手机号或邮箱，即可加入社区</p></header>

        <div class="login-mode-switch identifier-switch" role="tablist" aria-label="注册方式">
          <button type="button" role="tab" :aria-selected="form.identifierType === 'PHONE'" :class="{ active: form.identifierType === 'PHONE' }" @click="$emit('change-identifier-type', 'PHONE')">手机号</button>
          <button type="button" role="tab" :aria-selected="form.identifierType === 'EMAIL'" :class="{ active: form.identifierType === 'EMAIL' }" @click="$emit('change-identifier-type', 'EMAIL')">邮箱</button>
        </div>

        <div class="register-name-grid">
          <label>
            <span>昵称</span>
            <input v-model="form.nickname" type="text" autocomplete="nickname" maxlength="20" placeholder="例如：林澈" @input="$emit('clear-error')" />
          </label>
          <label>
            <span>用户名</span>
            <div class="register-prefixed-input">
              <i>@</i>
              <input
                v-model="form.username"
                type="text"
                autocomplete="username"
                maxlength="24"
                pattern="[a-z0-9_-]{3,24}"
                placeholder="linche"
                @input="form.username = form.username.toLowerCase().replace(/[^a-z0-9_-]/g, '').slice(0, 24); $emit('clear-error')"
              />
            </div>
          </label>
        </div>

        <label>
          <span>{{ form.identifierType === 'PHONE' ? '手机号' : '邮箱' }}</span>
          <input
            v-model="form.identifier"
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
            <button type="button" :disabled="codeSending || codeCountdown > 0" @click="$emit('send-code')">
              {{ codeSending ? '发送中…' : codeCountdown > 0 ? `${codeCountdown}s 后重发` : '获取验证码' }}
            </button>
          </div>
          <small v-if="maskedTarget" class="code-hint">验证码已发送至 {{ maskedTarget }}</small>
        </label>

        <label>
          <span>设置密码</span>
          <div class="password-field">
            <input v-model="form.password" :type="passwordVisible ? 'text' : 'password'" autocomplete="new-password" placeholder="至少 8 位，包含字母和数字" @input="$emit('clear-error')" />
            <button type="button" @click="$emit('toggle-password')">{{ passwordVisible ? '隐藏' : '显示' }}</button>
          </div>
        </label>

        <label>
          <span>确认密码</span>
          <div class="password-field">
            <input v-model="form.confirmPassword" :type="confirmPasswordVisible ? 'text' : 'password'" autocomplete="new-password" placeholder="再次输入密码" @input="$emit('clear-error')" />
            <button type="button" @click="$emit('toggle-confirm-password')">{{ confirmPasswordVisible ? '隐藏' : '显示' }}</button>
          </div>
        </label>

        <label class="register-agreement">
          <input v-model="form.agreed" type="checkbox" @change="$emit('clear-error')" />
          <span></span>
          <p>我已阅读并同意<a href="#" @click.prevent>社区公约</a>和<a href="#" @click.prevent>隐私政策</a></p>
        </label>

        <p v-if="error" class="login-error">{{ error }}</p>
        <button class="login-submit" type="submit">创建账号</button>
        <p class="login-register">已经有账号？<button type="button" @click="$emit('back-login')">返回登录</button></p>
      </form>
    </section>
  </main>
</template>
