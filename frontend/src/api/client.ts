import axios from 'axios';

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api';

export const apiClient = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 10000,
});

// Response interceptor for unified error formatting
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // Log in development for transparent debugging
    if (import.meta.env.DEV) {
      console.warn('API Error Interceptor:', error?.response?.data || error.message);
    }
    return Promise.reject(error);
  }
);
