import {Tokens} from '../constants';
import {environment} from './environment';
export default {
    setupInterceptors: (axios, isToken = false, isFormData = false) => {
        axios.interceptors.request.use(config => {
            const token = localStorage.getItem(Tokens.ADMIN);
            if (token) config.headers.Authorization = `Bearer ${token}`;
            // Let the browser add the multipart boundary.
            return config;
        });
        axios.interceptors.response.use(response => response, error => {
            if (!error.response) error.response = {status: 0, data: {message: 'Unable to reach the server. Please retry.'}};
            if (error.response.status === 401 && !String(error.config?.url).includes('login')) {
                [Tokens.ADMIN, Tokens.USER, Tokens.GET_PERMISSIONS].forEach(key => localStorage.removeItem(key));
                window.location.href = environment.URL + '#/login';
            }
            return Promise.reject(error);
        });
    }
};
