import type { NextConfig } from "next";

// const nextConfig: NextConfig = {
//   /* config options here */
// };

// export default nextConfig;

/** @type {import('next').NextConfig} */
const nextConfig = {
  async rewrites() {
    return [
      // https://example.com/api/orders  ->  http://54.180.135.117/api/orders
      {
        source: "/api/:path*",
        destination: "http://54.180.135.117/api/:path*",
      },
    ];
  },
};

module.exports = nextConfig;
