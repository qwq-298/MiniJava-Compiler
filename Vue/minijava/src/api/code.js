import axios from 'axios'

export function runCode(code) {

  return axios.post(
    'http://localhost:8080/api/run',
    code,
    {
      headers: {
        'Content-Type': 'text/plain'
      }
    }
  )

}

export function fetchAST(code) {

  return axios.post(
    'http://localhost:8080/api/ast',
    code,
    {
      headers: {
        'Content-Type': 'text/plain'
      }
    }
  )

}