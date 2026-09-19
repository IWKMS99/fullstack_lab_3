import axios, {type AxiosError, type InternalAxiosRequestConfig} from 'axios';
import type {AdminBooking, AdminBookingFilters, BookingResponse, CreateBookingPayload, ScheduleView} from '../types/booking.ts';
import type {AuthUser} from '../types/user.ts';
import type {
    AdminRoom,
    AdminRoomsFilters,
    CreateAdminRoomPayload,
    PageResponse,
    RoomFile,
    UpdateAdminRoomPayload,
} from '../types/adminRooms.ts';

export interface AdminUser {
    id: string;
    email: string;
    roles: string[];
}

export interface AuthResponse {
    token: string;
}

interface RetriableRequestConfig extends InternalAxiosRequestConfig {
    _retry?: boolean;
}

interface QueueItem {
    resolve: (token: string) => void;
    reject: (error: unknown) => void;
}

interface InterceptorOptions {
    getAccessToken: () => string | null;
    setAccessToken: (token: string) => void;
    onUnauthorized: () => void;
}

const apiClient = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
    withCredentials: true,
    headers: {
        'Content-Type': 'application/json',
    },
});

let isRefreshing = false;
let failedQueue: QueueItem[] = [];
let requestInterceptorId: number | null = null;
let responseInterceptorId: number | null = null;

const processQueue = (error: unknown, token: string | null) => {
    failedQueue.forEach(({resolve, reject}) => {
        if (error || !token) {
            reject(error);
            return;
        }
        resolve(token);
    });
    failedQueue = [];
};

const isRefreshRequest = (url?: string) => (url ?? '').includes('/auth/refresh');
const isAuthBootstrapEndpoint = (url?: string) => {
    const target = url ?? '';
    return target.includes('/auth/login') || target.includes('/auth/register') || target.includes('/auth/refresh');
};

export const setupInterceptors = ({getAccessToken, setAccessToken, onUnauthorized}: InterceptorOptions) => {
    if (requestInterceptorId !== null) {
        apiClient.interceptors.request.eject(requestInterceptorId);
    }
    if (responseInterceptorId !== null) {
        apiClient.interceptors.response.eject(responseInterceptorId);
    }

    requestInterceptorId = apiClient.interceptors.request.use(
        (config) => {
            const token = getAccessToken();
            if (token) {
                config.headers.Authorization = `Bearer ${token}`;
            }
            return config;
        },
        (error) => Promise.reject(error)
    );

    responseInterceptorId = apiClient.interceptors.response.use(
        (response) => response,
        async (error: AxiosError) => {
            const originalRequest = error.config as RetriableRequestConfig | undefined;
            const status = error.response?.status;

            if (!originalRequest || status !== 401) {
                return Promise.reject(error);
            }

            if (isRefreshRequest(originalRequest.url) || isAuthBootstrapEndpoint(originalRequest.url)) {
                return Promise.reject(error);
            }

            if (originalRequest._retry) {
                onUnauthorized();
                return Promise.reject(error);
            }

            originalRequest._retry = true;

            if (isRefreshing) {
                return new Promise((resolve, reject) => {
                    failedQueue.push({
                        resolve: (token: string) => {
                            originalRequest.headers.Authorization = `Bearer ${token}`;
                            resolve(apiClient(originalRequest));
                        },
                        reject,
                    });
                });
            }

            isRefreshing = true;

            try {
                const refreshResponse = await refreshAccessToken();
                setAccessToken(refreshResponse.token);
                processQueue(null, refreshResponse.token);
                originalRequest.headers.Authorization = `Bearer ${refreshResponse.token}`;
                return await apiClient(originalRequest);
            } catch (refreshError) {
                processQueue(refreshError, null);
                onUnauthorized();
                return Promise.reject(refreshError);
            } finally {
                isRefreshing = false;
            }
        }
    );

    return () => {
        if (requestInterceptorId !== null) {
            apiClient.interceptors.request.eject(requestInterceptorId);
            requestInterceptorId = null;
        }
        if (responseInterceptorId !== null) {
            apiClient.interceptors.response.eject(responseInterceptorId);
            responseInterceptorId = null;
        }
    };
};

export const fetchSchedule = async (date: string): Promise<ScheduleView> => {
    const response = await apiClient.get<ScheduleView>('/schedule', {
        params: {date},
    });
    return response.data;
};

export const getMyBookings = async (): Promise<BookingResponse[]> => {
    const response = await apiClient.get<BookingResponse[]>('/my-bookings');
    return response.data;
};

export const createBooking = async (payload: CreateBookingPayload): Promise<BookingResponse> => {
    const response = await apiClient.post<BookingResponse>('/bookings', payload);
    return response.data;
};

export const cancelBooking = async (bookingId: string): Promise<void> => {
    await apiClient.delete(`/bookings/${bookingId}`);
};

export const registerUser = async (payload: {email: string; password: string}): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/auth/register', payload);
    return response.data;
};

export const loginUser = async (payload: {email: string; password: string}): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/auth/login', payload);
    return response.data;
};

export const refreshAccessToken = async (): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/auth/refresh');
    return response.data;
};

export const logoutUser = async (): Promise<void> => {
    await apiClient.post('/auth/logout');
};

export const getCurrentUser = async (): Promise<AuthUser> => {
    const response = await apiClient.get<AuthUser>('/auth/me');
    return response.data;
};

export const getAdminUsers = async (): Promise<AdminUser[]> => {
    const response = await apiClient.get<AdminUser[]>('/admin/users');
    return response.data;
};

export const updateUserRole = async (userId: string, role: 'ROLE_USER' | 'ROLE_ADMIN'): Promise<AdminUser> => {
    const response = await apiClient.put<AdminUser>(`/admin/users/${userId}/role`, {role});
    return response.data;
};

export const getAdminBookings = async (filters: AdminBookingFilters): Promise<AdminBooking[]> => {
    const response = await apiClient.get<AdminBooking[]>('/admin/bookings', {
        params: filters,
    });
    return response.data;
};

export const cancelAdminBooking = async (bookingId: string): Promise<void> => {
    await apiClient.delete(`/admin/bookings/${bookingId}`);
};

export const getAdminRooms = async (filters: AdminRoomsFilters): Promise<PageResponse<AdminRoom>> => {
    const response = await apiClient.get<PageResponse<AdminRoom>>('/admin/rooms', {
        params: filters,
    });
    return response.data;
};

export const createAdminRoom = async (payload: CreateAdminRoomPayload): Promise<AdminRoom> => {
    const response = await apiClient.post<AdminRoom>('/admin/rooms', payload);
    return response.data;
};

export const updateAdminRoom = async (roomId: string, payload: UpdateAdminRoomPayload): Promise<AdminRoom> => {
    const response = await apiClient.put<AdminRoom>(`/admin/rooms/${roomId}`, payload);
    return response.data;
};

export const deleteAdminRoom = async (roomId: string): Promise<void> => {
    await apiClient.delete(`/admin/rooms/${roomId}`);
};

export const uploadRoomFile = async (roomId: string, file: File): Promise<RoomFile> => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await apiClient.post<RoomFile>(`/admin/rooms/${roomId}/files`, formData, {
        headers: {'Content-Type': 'multipart/form-data'},
    });
    return response.data;
};

export const getRoomFiles = async (roomId: string): Promise<RoomFile[]> => {
    const response = await apiClient.get<RoomFile[]>(`/admin/rooms/${roomId}/files`);
    return response.data;
};

export const deleteRoomFile = async (fileId: string): Promise<void> => {
    await apiClient.delete(`/admin/rooms/files/${fileId}`);
};
