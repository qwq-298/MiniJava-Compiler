<template>

<div class="register-page">

  <div class="register-box">

    <h1>Create Account</h1>

    <input
      v-model="username"
      placeholder="Username"
    />

    <input
      v-model="password"
      type="password"
      placeholder="Password"
    />

    <button @click="register">
      Register
    </button>

    <p class="link">

      Already have account?

      <span @click="goLogin">
        Login
      </span>

    </p>

  </div>

</div>

</template>

<script>

import axios from 'axios'

export default {

  data() {

    return {

      username: '',
      password: ''

    }

  },

  methods: {

    async register() {

      try {

        const res = await axios.post(

          'http://localhost:8080/api/login/register',

          {

            username: this.username,
            password: this.password

          }

        )

        alert(res.data.message)

        if (res.data.success) {

          this.$router.push('/login')

        }

      } catch(err) {

        console.error(err)

      }

    },

    goLogin() {

      this.$router.push('/login')

    }

  }

}

</script>

<style>

.register-page {

  height: 100vh;

  display: flex;

  justify-content: center;

  align-items: center;

  background: #1e1e1e;

}

.register-box {

  width: 320px;

  background: #2d2d2d;

  padding: 30px;

  border-radius: 10px;

  display: flex;

  flex-direction: column;

  gap: 15px;

}

.register-box h1 {

  color: white;

  text-align: center;

}

.register-box input {

  padding: 10px;

  background: #1e1e1e;

  border: 1px solid #444;

  color: white;

}

.register-box button {

  padding: 10px;

  background: #4caf50;

  border: none;

  color: white;

  cursor: pointer;

}

.link {

  color: #aaa;

  text-align: center;

}

.link span {

  color: #4caf50;

  cursor: pointer;

}

</style>