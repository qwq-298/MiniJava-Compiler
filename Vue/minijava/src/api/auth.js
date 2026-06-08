import axios from 'axios'

const BASE =
'http://localhost:8080/api/login'

export function login(data) {

  return axios.post(
    BASE + '/login',
    data
  )

}

export function register(data) {

  return axios.post(
    BASE + '/register',
    data
  )

}