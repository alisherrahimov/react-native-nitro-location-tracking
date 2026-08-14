import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

const config: Config = {
  title: 'Nitro Location Tracking',
  tagline:
    'High-performance background location tracking for React Native, built on Nitro Modules',
  favicon: 'img/favicon.svg',

  future: {
    v4: true,
  },

  // GitHub Pages deployment config
  url: 'https://alisherrahimov.github.io',
  baseUrl: '/react-native-nitro-location-tracking/',
  organizationName: 'alisherrahimov',
  projectName: 'react-native-nitro-location-tracking',
  deploymentBranch: 'gh-pages',
  trailingSlash: false,

  onBrokenLinks: 'throw',
  markdown: {
    hooks: {
      onBrokenMarkdownLinks: 'warn',
    },
  },

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          editUrl:
            'https://github.com/alisherrahimov/react-native-nitro-location-tracking/tree/main/docs/',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    image: 'img/social-card.jpg',
    colorMode: {
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: 'Nitro Location Tracking',
      logo: {
        alt: 'Nitro Location Tracking logo',
        src: 'img/logo.svg',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'docsSidebar',
          position: 'left',
          label: 'Docs',
        },
        {
          href: 'https://www.npmjs.com/package/react-native-nitro-location-tracking',
          label: 'npm',
          position: 'right',
        },
        {
          href: 'https://github.com/alisherrahimov/react-native-nitro-location-tracking',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Docs',
          items: [
            {label: 'Introduction', to: '/docs/intro'},
            {label: 'Installation', to: '/docs/installation'},
            {label: 'API Reference', to: '/docs/api/types'},
          ],
        },
        {
          title: 'Community',
          items: [
            {
              label: 'GitHub Issues',
              href: 'https://github.com/alisherrahimov/react-native-nitro-location-tracking/issues',
            },
            {
              label: 'Contributing',
              to: '/docs/contributing',
            },
          ],
        },
        {
          title: 'More',
          items: [
            {
              label: 'npm package',
              href: 'https://www.npmjs.com/package/react-native-nitro-location-tracking',
            },
            {
              label: 'Changelog',
              href: 'https://github.com/alisherrahimov/react-native-nitro-location-tracking/blob/main/CHANGELOG.md',
            },
            {
              label: 'GitHub',
              href: 'https://github.com/alisherrahimov/react-native-nitro-location-tracking',
            },
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} react-native-nitro-location-tracking. Built with Docusaurus.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['bash', 'swift', 'kotlin', 'groovy'],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
