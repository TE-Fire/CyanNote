<template>
  <div v-if="visible" class="fixed inset-0 flex items-center justify-center" style="z-index: 9999;">
    <!-- 遮罩层 -->
    <div class="absolute inset-0 bg-gray-800/25" style="z-index: 9998;" @click="onClose"></div>
    
    <!-- 登录框 -->
    <Transition 
      name="zoom"
      appear
      @before-enter="onBeforeEnter"
      @enter="onEnter"
      @leave="onLeave"
    >
      <div class="relative bg-white rounded-lg w-[480px]" style="z-index: 9999;" ref="modalRef">
        <!-- 关闭按钮 -->
        <button 
          class="absolute right-6 top-6 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-full p-2" 
          @click="onClose"
        >
          <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor">
            <path d="M6 18L18 6M6 6l12 12" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </button>

        <!-- 登录内容 -->
        <div class="text-center px-[72px] py-10">
          <h2 class="text-[18px] font-bold mb-12">手机号登录</h2>
          
          <!-- 手机号输入 -->
          <div class="relative mb-4">
            <div class="flex items-center bg-[#f4f4f4] rounded-3xl h-[48px] px-4">
              <div class="flex items-center text-[15px] text-gray-800">
                <span>+86</span>
                <div class="mx-2 w-[1px] h-[14px] bg-[#ddd]"></div>
              </div>
              <input 
                type="text" 
                placeholder="输入手机号" 
                class="flex-1 outline-none text-[15px] ml-1 bg-transparent caret-[#0E9384]"
                v-model="formattedPhone"
                maxlength="13"
                @input="formatPhoneNumber"
              >
            </div>
          </div>

          <!-- 验证码输入 -->
          <div class="relative mb-3">
            <div class="flex items-center bg-[#f4f4f4] rounded-3xl h-[48px] px-4">
              <input 
                type="text" 
                placeholder="输入验证码" 
                class="flex-1 outline-none text-[15px] bg-transparent caret-[#0E9384]"
                v-model="code"
                maxlength="6"
                @input="formatCode"
              >
              <button 
                class="text-[15px] font-medium ml-4 cursor-pointer transition-colors"
                :class="buttonClass"
                @click="getCode"
                :disabled="!isPhoneValid || countdown > 0"
              >
                {{ countdown > 0 ? `重新发送(${countdown}s)` : '获取验证码' }}
              </button>
            </div>
          </div>

          <!-- 演示环境：验证码直接展示 -->
          <div 
            v-if="smsCode" 
            class="mb-6 flex items-center justify-between rounded-xl bg-[#E7F4F1] px-4 py-2.5 text-left"
          >
            <span class="text-[13px] text-[#5b7b76]">
              演示环境不发短信，验证码
            </span>
            <span class="text-[15px] font-bold tracking-[0.22em] text-[#0B7A6E]">{{ smsCode }}</span>
          </div>
          <div v-else class="mb-6"></div>

          <!-- 登录按钮 -->
          <button 
            class="w-full bg-[#0E9384] text-white rounded-full h-[48px] text-[16px] cursor-pointer
            font-bold hover:bg-opacity-90 mt-5"
            @click="handleLogin"
          >
            登录
          </button>

          <!-- 协议勾选 -->
          <div class="mt-5 text-xs text-gray-700">
            <div class="flex justify-center gap-1">
              <input type="checkbox" class="w-4 h-4" v-model="agreeTerms">
              <div>
                <span>我已阅读并同意</span>
                <a href="#" class="text-[#0B7A6E] cursor-pointer">《用户协议》</a>
                <a href="#" class="text-[#0B7A6E] cursor-pointer">《隐私政策》</a>
                <a href="#" class="text-[#0B7A6E] cursor-pointer">《儿童/青少年个人信息保护规则》</a>
              </div>
            </div>
          </div>

          <!-- 新用户提示 -->
          <div class="mt-8 text-[14px] text-gray-500">
            新用户可直接登录
          </div>
        </div>
      </div>
    </Transition>

    <!-- 添加协议确认模态框 -->
    <TermsConfirmModal
      v-model:visible="showTermsConfirm"
      @confirm="handleConfirmTerms"
    />
  </div>
</template>

<script setup>
import { ref, computed, onUnmounted, watch } from 'vue'
import gsap from 'gsap'
import { message } from '@/utils/message'
import TermsConfirmModal from './TermsConfirmModal.vue'
import { login, getVerificationCode } from '@/api/auth'
import { getUserProfile } from '@/api/user'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:visible'])

const phone = ref('')
const formattedPhone = ref('')
const code = ref('')
const smsCode = ref('')
const agreeTerms = ref(false)

const modalRef = ref(null)

const showTermsConfirm = ref(false)

// 倒计时
const countdown = ref(0)
let timer = null

const onClose = () => {
  emit('update:visible', false)
}

// 验证手机号格式
const isPhoneValid = computed(() => {
  return phone.value.length === 11 && /^1[3-9]\d{9}$/.test(phone.value)
})

// 按钮样式计算属性
const buttonClass = computed(() => {
  if (countdown.value > 0) {
    return 'text-[#0E9384]/40 cursor-not-allowed'
  }
  return isPhoneValid.value 
    ? 'text-[#0E9384] hover:text-[#0B7A6E]' 
    : 'text-[#0E9384]/60 cursor-not-allowed'
})

// 获取验证码
let codePhone = ''
const getCode = (force = false) => {
  if (!isPhoneValid.value) return
  if (!force && countdown.value > 0) return
  
  // 演示环境：验证码由服务端随机生成后返回，直接展示并自动填入
  getVerificationCode(phone.value).then(res => {
    if (!res.success) {
      message.show(res.message)
      return
    }
    
    codePhone = phone.value
    smsCode.value = res.data
    code.value = res.data
    
    // 开始倒计时
    countdown.value = 180 // 3分钟
    clearInterval(timer)
    timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  })
}

// 手机号填满即自动取码；换号后重新取，避免展示上一个号的验证码
watch(phone, (value) => {
  if (value.length === 11 && value !== codePhone) getCode(true)
})

// 组件卸载时清除定时器
onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
  }
})

// 处理登录
const handleLogin = () => {
  if (!agreeTerms.value) {
    showTermsConfirm.value = true
    return
  }
  
  doLogin()
}

// 确认同意协议
const handleConfirmTerms = () => {
  agreeTerms.value = true
  doLogin()
}

// 执行登录
const doLogin = () => {
  if (!isPhoneValid.value) {
    message.show('请输入正确的手机号')
    return
  }
  
  if (!code.value || code.value.length !== 6) {
    message.show('请输入正确的验证码')
    return
  }
  
  // 调用登录接口
  login({phone: phone.value, code: code.value, type: 1}).then(res => {
    console.log(res)
    if (!res.success) {
      message.show('验证码错误')
      return
    }

    // 存储 token
    userStore.setToken(res.data)

    // 获取用户基本信息
    getUserProfile().then(res => {
      if (res.success) {
        userStore.setProfile(res.data)
      }
    })
    
    message.show('登录成功')
    onClose() // 关闭登录框
  })
}

const onBeforeEnter = (el) => {
  gsap.set(el, {
    opacity: 0,
    scale: 0.5,
    y: 40
  })
}

const onEnter = (el) => {
  gsap.to(el, {
    opacity: 1,
    scale: 1,
    y: 0,
    duration: 0.3,
    ease: 'back.out(1.7)'
  })
}

const onLeave = (el) => {
  gsap.to(el, {
    opacity: 0,
    scale: 0.5,
    duration: 0.2,
    ease: 'power2.in'
  })
}

// 格式化手机号
const formatPhoneNumber = (event) => {
  // 移除所有非数字字符
  let value = event.target.value.replace(/\D/g, '')
  
  // 限制长度为11位数字
  if (value.length > 11) {
    value = value.slice(0, 11)
  }
  
  // 添加空格: xxx xxxx xxxx
  if (value.length > 7) {
    formattedPhone.value = `${value.slice(0, 3)} ${value.slice(3, 7)} ${value.slice(7)}`
  } else if (value.length > 3) {
    formattedPhone.value = `${value.slice(0, 3)} ${value.slice(3)}`
  } else {
    formattedPhone.value = value
  }
  
  // 更新实际的手机号(不带空格)
  phone.value = value
}

// 格式化验证码 - 只允许输入数字
const formatCode = (event) => {
  // 移除所有非数字字符
  let value = event.target.value.replace(/\D/g, '')
  
  // 限制长度为6位数字
  if (value.length > 6) {
    value = value.slice(0, 6)
  }
  
  code.value = value
}
</script>

<style scoped>
.zoom-move {
  transition: transform 0.3s ease-out;
}

/* 可选：禁用 Chrome 浏览器的自动填充背景色 */
input:-webkit-autofill,
input:-webkit-autofill:hover,
input:-webkit-autofill:focus {
  -webkit-box-shadow: 0 0 0 30px white inset !important;
}
</style>