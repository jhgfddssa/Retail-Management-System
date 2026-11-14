const configuredBackendOrigin = process.env.RENDER_BACKEND_URL;
let backendOrigin;

try {
  const parsed = new URL(configuredBackendOrigin);
  if (parsed.protocol !== 'https:' || parsed.pathname !== '/' || parsed.search || parsed.hash || parsed.username || parsed.password) {
    throw new Error();
  }
  backendOrigin = parsed.origin;
} catch {
  throw new Error('Set RENDER_BACKEND_URL to the deployed HTTPS backend origin.');
}

export const config = {
  rewrites: [
    { source: '/api/:path*', destination: `${backendOrigin}/api/:path*` },
    { source: '/(.*)', destination: '/index.html' },
  ],
};
