import {useEffect, useRef, useState} from 'react';
import {authClient} from '../../clients/authClient';
import {ApiError} from '../../clients/httpClient';
import {authErrorMessage} from '../../util/authError';

type CredentialResponse = { credential: string };
type GoogleApi = {
    accounts: {
        id: {
            initialize: (config: {
                client_id: string;
                nonce: string;
                callback: (response: CredentialResponse) => void;
                auto_select: boolean
            }) => void;
            renderButton: (container: HTMLElement, config: {
                theme: string;
                size: string;
                width: number;
                text: string
            }) => void;
        }
    }
};
declare global {
    interface Window {
        google?: GoogleApi
    }
}

let scriptPromise: Promise<void> | null = null;

function loadGoogleScript(): Promise<void> {
    if (window.google?.accounts?.id) return Promise.resolve();
    if (scriptPromise) return scriptPromise;
    scriptPromise = new Promise<void>((resolve, reject) => {
        const script = document.createElement('script');
        script.src = 'https://accounts.google.com/gsi/client';
        script.async = true;
        script.onload = () => {
            if (window.google?.accounts?.id) resolve();
            else {
                scriptPromise = null;
                reject(new Error('Google SDK không khả dụng. Kiểm tra tiện ích chặn nội dung hoặc trình duyệt.'));
            }
        };
        script.onerror = () => {
            scriptPromise = null;
            script.remove();
            reject(new Error('Không tải được Google Sign-In. Kiểm tra kết nối tới accounts.google.com.'));
        };
        document.head.appendChild(script);
    });
    return scriptPromise;
}

/** Always show a recognizable provider label, including when setup or the network fails. */
function GoogleMark() {
    return <svg className="google-auth-mark" viewBox="0 0 48 48" aria-hidden="true" focusable="false">
        <path fill="#EA4335"
              d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
        <path fill="#4285F4"
              d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6C44.4 38.03 46.98 31.85 46.98 24.55z"/>
        <path fill="#FBBC05"
              d="M10.53 28.59A14.4 14.4 0 0 1 9.75 24c0-1.59.27-3.14.76-4.59l-7.98-6.19A23.88 23.88 0 0 0 0 24c0 3.87.93 7.52 2.56 10.78l7.97-6.19z"/>
        <path fill="#34A853"
              d="M24 48c6.48 0 11.93-2.13 15.9-5.8l-7.73-6c-2.15 1.45-4.92 2.3-8.17 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.97 6.19C6.51 42.62 14.62 48 24 48z"/>
    </svg>;
}

export function GoogleAuthButton({mode, onSuccess}: {
    mode: 'signin' | 'link'; onSuccess: () => Promise<void> | void;
}) {
    const container = useRef<HTMLDivElement>(null);
    const submitting = useRef(false);
    const [attempt, setAttempt] = useState(0);
    const [state, setState] = useState<'loading' | 'ready' | 'error'>('loading');
    const [error, setError] = useState('');
    const clientId = (import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined)?.trim();
    const configured = Boolean(clientId && clientId.endsWith('.apps.googleusercontent.com'));

    useEffect(() => {
        if (!configured || !clientId) return;
        let active = true;
        submitting.current = false;
        setState('loading');
        setError('');
        const host = container.current;
        if (host) host.replaceChildren();

        void (async () => {
            try {
                // A nonce from the backend is required; do not use Google tokens without it.
                const nonce = await authClient.googleNonce();
                await loadGoogleScript();
                if (!active || !host || !window.google?.accounts?.id) return;
                window.google.accounts.id.initialize({
                    client_id: clientId,
                    nonce,
                    auto_select: false,
                    callback: (response) => {
                        if (!active || submitting.current) return;
                        submitting.current = true;
                        void (async () => {
                            try {
                                setError('');
                                if (mode === 'link') await authClient.linkGoogle(response.credential);
                                else await authClient.googleSignIn(response.credential);
                                if (active) await onSuccess();
                            } catch (reason) {
                                if (active) setError(reason instanceof ApiError && reason.status === 409
                                    ? (mode === 'signin'
                                        ? 'Email này đã có tài khoản GO Cook. Hãy đăng nhập bằng mật khẩu rồi liên kết Google trong Hồ sơ.'
                                        : 'Email Google phải trùng email GO Cook và chưa được liên kết với tài khoản khác.')
                                    : authErrorMessage(reason, 'Không thể xác thực bằng Google.'));
                            } finally {
                                submitting.current = false;
                            }
                        })();
                    },
                });
                host.replaceChildren();
                window.google.accounts.id.renderButton(host, {
                    theme: 'outline', size: 'large', width: Math.min(400, host.clientWidth || 400),
                    text: mode === 'link' ? 'continue_with' : 'signin_with',
                });
                if (active) setState('ready');
            } catch (reason) {
                if (active) {
                    setState('error');
                    setError(authErrorMessage(reason, 'Không thể kết nối Google Sign-In.'));
                }
            }
        })();
        return () => {
            active = false;
            if (host) host.replaceChildren();
        };
    }, [clientId, configured, mode, onSuccess, attempt]);

    return <section className="google-auth-block"
                    aria-label={mode === 'link' ? 'Liên kết Google' : 'Đăng nhập với Google'}>
        {!configured ? (
            <p className="small muted" role="status">
                Đăng nhập Google chưa được cấu hình. Thêm VITE_GOOGLE_CLIENT_ID vào frontend/.env.local rồi khởi động
                lại Vite.
            </p>
        ) : <>
            {state === 'loading' && <p className="small muted" role="status">Đang tải nút Google...</p>}
            {state === 'ready' && !error}
            <div ref={container} className="google-auth-button-slot"/>
            {error && <p className="small google-auth-error" role="alert">{error}</p>}
            {error &&
                <button className="btn secondary sm" type="button" onClick={() => setAttempt((old) => old + 1)}>Lấy mã
                    mới và thử lại bằng Google</button>}
        </>}
    </section>;
}
