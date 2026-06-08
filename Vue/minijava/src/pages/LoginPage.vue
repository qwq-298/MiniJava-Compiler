<template>

<div class="login-page">

  <div class="login-box">

    <h1>MiniJava IDE</h1>

    <input
      v-model="username"
      placeholder="Username"
    />

    <input
      v-model="password"
      type="password"
      placeholder="Password"
    />

    <button @click="login">
      Login
    </button>

    <p class="link">
      No account?

      <span @click="goRegister">
        Register
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

    async login() {

      try {

        const res = await axios.post(

          'http://localhost:8080/api/login/login',

          {

            username: this.username,
            password: this.password

          }

        )

        if (res.data.success) {

          localStorage.setItem(
            'userid',
            res.data.userid
          )

          localStorage.setItem(
            'username',
            res.data.username
          )

          this.$router.push('/ide')

        } else {

          alert(res.data.message)

        }

      } catch(err) {

        console.error(err)

      }

    },

    goRegister() {

      this.$router.push('/register')

    }

  }

}

</script>

<style>

.login-page {

  height: 100vh;

  display: flex;

  justify-content: center;

  align-items: center;

  background: #1e1e1e;

}

.login-box {

  width: 320px;

  background: #2d2d2d;

  padding: 30px;

  border-radius: 10px;

  display: flex;

  flex-direction: column;

  gap: 15px;

}

.login-box h1 {

  color: white;

  text-align: center;

}

.login-box input {

  padding: 10px;

  background: #1e1e1e;

  border: 1px solid #444;

  color: white;

}

.login-box button {

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