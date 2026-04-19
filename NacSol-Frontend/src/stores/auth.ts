import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '@/lib/axios'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<{ username: string } | null>(null)

  const isAuthenticated = computed(() => !!user.value)

  async function login(username: string, password: string) {
    await api.post('/auth/login', { username, password })
    // Spring sets the HttpOnly cookie — just record the user in memory
    user.value = { username }
  }

  // Called on app boot to restore auth state after a page refresh
  async function fetchMe() {
    try {
      const { data } = await api.get('/auth/me')
      user.value = data
    } catch {
      user.value = null
    }
  }

  function logout() {
    user.value = null
    api.post('/auth/logout').catch(() => {})
  }

  return { user, isAuthenticated, login, logout, fetchMe }
})
