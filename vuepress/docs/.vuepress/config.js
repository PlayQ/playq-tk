import {webpackBundler} from '@vuepress/bundler-webpack'
import {defaultTheme} from '@vuepress/theme-default'
import {defineUserConfig} from 'vuepress'

export default defineUserConfig({
    bundler: webpackBundler(),
    theme: defaultTheme({
        navbar: [
            {
                text: "D4S",
                children: [
                    {
                        text: 'D4S',
                        link: "/d4s/about/"
                    },
                    {
                        text: "Documentation",
                        link: "/d4s/docs/"
                    },
                    {
                        text: "Resources",
                        link: "/d4s/resources/"
                    },
                ]
            },
            {
                text: 'About',
                link: '/about/'
            },
            {
                text: 'Github',
                link: 'https://github.com/PlayQ/playq-tk'
            },
        ],
        sidebar: {
            '/d4s/docs/': [{
                text: 'D4S',
                collapsable: false,
                children: [
                    'tutorial',
                    'setup',
                    'table-definition',
                    'basic-queries',
                    'batched-operations',
                    'conditionals',
                    'indexes'
                ]
            }]
        }
    }),
    base: 'playq-tk',
    head: [
        ['link', {
            rel: 'icon',
            href: '/D4S_logo.svg'
        }]
    ],
    locales: {
        '/': {
            lang: 'en-US',
            title: 'PlayQ ToolKit',
            description: 'Izumi powered ToolKit.',
        }
    },
})