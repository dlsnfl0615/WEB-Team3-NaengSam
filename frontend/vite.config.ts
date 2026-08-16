import {fileURLToPath, URL} from 'node:url'
import {defineConfig} from 'vite'
import react, {reactCompilerPreset} from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'
import tailwindcss from '@tailwindcss/vite'
import {VitePWA} from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
    plugins: [
        react(),
        babel({presets: [reactCompilerPreset()]}),
        tailwindcss(),
        VitePWA({
            strategies: 'injectManifest',
            srcDir: 'src',
            filename: 'sw.ts',
            registerType: 'autoUpdate',
            injectRegister: 'auto',
            injectManifest: {
                globPatterns: ['**/*.{js,css,html,svg,png,ico,woff2}'],
            },
            includeAssets: ['favicon.ico', 'apple-touch-icon-180x180.png'],
            manifest: {
                name: '쉼,부름',
                short_name: '쉼,부름',
                description: '쉼,부름 — 배달 매칭 서비스',
                lang: 'ko',
                start_url: '/',
                scope: '/',
                display: 'standalone',
                orientation: 'portrait',
                background_color: '#f7f8fa', // --color-canvas
                theme_color: '#0d1b3d', // --color-navy-900
                icons: [
                    {src: '/pwa-192x192.png', sizes: '192x192', type: 'image/png'},
                    {src: '/pwa-512x512.png', sizes: '512x512', type: 'image/png'},
                    {
                        src: '/pwa-512x512-maskable.png',
                        sizes: '512x512',
                        type: 'image/png',
                        purpose: 'maskable',
                    },
                ],
            },
            devOptions: {
                enabled: true,
                type: 'module',
                navigateFallback: 'index.html',
            },
        }),
    ],
    resolve: {
        alias: {
            '@': fileURLToPath(new URL('./src', import.meta.url)),
        },
    },
    server: {
        // 세션 쿠키(JSESSIONID)를 동일 출처로 흐르게 하는 개발 프록시(개발 전용 — 운영엔 없음).
        // 생성 클라이언트의 요청 URL이 `/api/v1/...` 이므로 그대로 백엔드로 전달된다.
        // 원격 백엔드로 붙일 땐 DEV_API_TARGET 환경변수로 타깃을 바꾼다(기본 로컬 8080).
        proxy: {
            '/api': {
                target: process.env.DEV_API_TARGET ?? 'http://localhost:8080',
                changeOrigin: true,
            },
        },
    },
})
