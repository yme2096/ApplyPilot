import axios from 'axios';
export const api=axios.create({baseURL:import.meta.env.VITE_API_URL??'http://localhost:8081/api'});
api.interceptors.request.use(c=>{const t=sessionStorage.getItem('accessToken');if(t)c.headers.Authorization=`Bearer ${t}`;return c;});
api.interceptors.response.use(r=>r,e=>{if(e.response?.status===401){sessionStorage.removeItem('accessToken');sessionStorage.removeItem('refreshToken');sessionStorage.removeItem('email');window.dispatchEvent(new Event('applypilot:session-expired'));}return Promise.reject(e);});
