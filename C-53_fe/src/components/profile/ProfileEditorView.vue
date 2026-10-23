<script setup>
defineProps({
  draft: { type: Object, required: true },
  birthPickerOpen: { type: Boolean, default: false },
  formattedBirthDate: { type: String, required: true },
  birthDateParts: { type: Object, required: true },
  birthPickerYears: { type: Array, required: true },
  birthPickerDays: { type: Number, required: true },
  age: { type: [Number, String], required: true },
  zodiac: { type: String, required: true },
})

defineEmits(['close', 'save', 'avatar-upload', 'open-birth-picker', 'close-birth-picker', 'select-birth-part', 'clear-birth-date'])
</script>

<template>
  <main class="profile-edit-page">
    <div class="profile-edit-toolbar">
      <button class="detail-back" type="button" @click="$emit('close')"><span aria-hidden="true">←</span> 返回个人主页</button>
      <div><button class="text-button" type="button" @click="$emit('close')">取消</button><button class="primary-button" type="button" @click="$emit('save')">保存资料</button></div>
    </div>

    <form class="profile-edit-layout" @submit.prevent="$emit('save')">
      <aside class="avatar-editor">
        <p class="kicker">PROFILE PHOTO</p>
        <div class="editable-avatar"><img v-if="draft.avatar" :src="draft.avatar" alt="头像预览" /><span v-else>{{ draft.nickname.trim().slice(0, 1) || '许' }}</span><i></i></div>
        <label class="avatar-upload"><input type="file" accept="image/png,image/jpeg,image/webp" @change="$emit('avatar-upload', $event)" />更换头像</label>
        <p>支持 JPG、PNG、WebP<br />文件不超过 4MB</p>
      </aside>

      <div class="profile-form">
        <header><p class="kicker">EDIT PROFILE</p><h1>编辑个人资料</h1><p>完善公开身份与联系方式。带星号的字段为必填项。</p></header>

        <section class="form-section">
          <div class="form-section-heading"><span>01</span><div><h2>基础身份</h2><p>这些信息会显示在个人主页顶部。</p></div></div>
          <div class="profile-form-grid">
            <label><span>昵称 *</span><input v-model="draft.nickname" type="text" maxlength="20" required placeholder="请输入昵称" /></label>
            <label><span>用户名 *</span><div class="prefixed-input"><i>@</i><input v-model="draft.username" type="text" maxlength="24" required placeholder="username" /></div></label>
            <label class="full"><span>真实姓名 <small>可选</small></span><input v-model="draft.realName" type="text" maxlength="30" placeholder="仅在需要实名展示时填写" autocomplete="name" /></label>
            <label class="full"><span>个人简介</span><textarea v-model="draft.bio" rows="3" maxlength="120" placeholder="简单介绍一下自己"></textarea><small class="field-count">{{ draft.bio.length }}/120</small></label>
          </div>
        </section>

        <section class="form-section">
          <div class="form-section-heading"><span>02</span><div><h2>个人信息</h2><p>年龄和星座将根据出生日期自动计算。</p></div></div>
          <div class="profile-form-grid">
            <label><span>性别</span><select v-model="draft.gender"><option>保密</option><option>男</option><option>女</option><option>自定义</option></select></label>
            <div class="date-field">
              <span>出生日期</span>
              <button class="date-trigger" type="button" :aria-expanded="birthPickerOpen" @click="$emit('open-birth-picker')"><span :class="{ placeholder: !draft.birthDate }">{{ formattedBirthDate }}</span><i class="calendar-icon" aria-hidden="true"></i></button>
              <Transition name="date-picker">
                <div v-if="birthPickerOpen" class="date-picker-backdrop" @click.self="$emit('close-birth-picker')" @keydown.esc="$emit('close-birth-picker')">
                  <section id="birth-picker-panel" class="date-picker-panel" role="dialog" aria-modal="true" aria-label="选择出生日期" tabindex="-1">
                    <header><div><p class="kicker">BIRTH DATE</p><h3>选择出生日期</h3></div><button type="button" aria-label="关闭日期选择器" @click="$emit('close-birth-picker')">×</button></header>
                    <div class="date-picker-value"><strong>{{ birthDateParts.year }}</strong><span>{{ String(birthDateParts.month).padStart(2, '0') }} / {{ String(birthDateParts.day).padStart(2, '0') }}</span></div>
                    <div class="date-wheels">
                      <div class="date-wheel"><span>年份</span><div class="date-wheel-scroll"><button v-for="year in birthPickerYears" :key="year" type="button" :class="{ selected: birthDateParts.year === year }" :aria-pressed="birthDateParts.year === year" @click="$emit('select-birth-part', 'year', year)">{{ year }}</button></div></div>
                      <div class="date-wheel"><span>月份</span><div class="date-wheel-scroll"><button v-for="month in 12" :key="month" type="button" :class="{ selected: birthDateParts.month === month }" :aria-pressed="birthDateParts.month === month" @click="$emit('select-birth-part', 'month', month)">{{ month }} 月</button></div></div>
                      <div class="date-wheel"><span>日期</span><div class="date-wheel-scroll"><button v-for="day in birthPickerDays" :key="day" type="button" :class="{ selected: birthDateParts.day === day }" :aria-pressed="birthDateParts.day === day" @click="$emit('select-birth-part', 'day', day)">{{ day }} 日</button></div></div>
                    </div>
                    <footer><button class="date-clear" type="button" @click="$emit('clear-birth-date')">清除日期</button><button class="date-confirm" type="button" @click="$emit('close-birth-picker')">完成</button></footer>
                  </section>
                </div>
              </Transition>
            </div>
            <label class="generated-field"><span>年龄 <small>自动生成</small></span><output>{{ age === '—' ? '—' : age + ' 岁' }}</output></label>
            <label class="generated-field"><span>星座 <small>自动生成</small></span><output>{{ zodiac }}</output></label>
          </div>
        </section>

        <section class="form-section">
          <div class="form-section-heading"><span>03</span><div><h2>地区与地址</h2><p>帮助社区成员了解你所在的城市。</p></div></div>
          <div class="profile-form-grid">
            <label><span>所在地区</span><input v-model="draft.region" type="text" placeholder="例如：中国 · 浙江" /></label><label><span>城市</span><input v-model="draft.city" type="text" placeholder="例如：杭州" /></label>
            <label class="full"><span>常驻地址</span><textarea v-model="draft.address" rows="2" placeholder="可填写常驻区域或详细地址"></textarea></label>
          </div>
        </section>

        <section class="form-section">
          <div class="form-section-heading"><span>04</span><div><h2>联系方式</h2><p>联系方式默认不在公开主页直接展示。</p></div></div>
          <div class="profile-form-grid"><label><span>联系电话</span><input v-model="draft.phone" type="tel" placeholder="请输入联系电话" autocomplete="tel" /></label><label><span>电子邮箱</span><input v-model="draft.email" type="email" placeholder="name@example.com" autocomplete="email" /></label></div>
        </section>

        <div class="profile-form-actions"><button class="text-button" type="button" @click="$emit('close')">取消</button><button class="primary-button" type="submit">保存资料</button></div>
      </div>
    </form>
  </main>
</template>
