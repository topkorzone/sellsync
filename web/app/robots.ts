import type { MetadataRoute } from 'next';

export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      {
        userAgent: '*',
        allow: ['/', '/pricing', '/login', '/register'],
        disallow: [
          '/api/',
          '/dashboard/',
          '/settings/',
          '/orders/',
          '/postings/',
          '/sale-forms/',
          '/shipments/',
          '/sync/',
          '/mappings/',
          '/setup/',
          '/test-*',
          '/oauth/',
        ],
      },
    ],
    sitemap: 'https://sell-sync.biz/sitemap.xml',
  };
}
