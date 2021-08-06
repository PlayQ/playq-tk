module.exports = {
  base: '/playq-tk/',
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
  themeConfig: {
    locales: {
      '/': {
        selectText: 'Language',
        label: 'English',
        nav: [{
            text: 'D4S',
            items: [
                {
                    text: "About",
                    link: "/d4s/about/"
                },
                {
                    text: "Documentation",
                    link: "/d4s/docs/"
                },
                {
                    text: "Resources",
                    link: "/d4s/resources/"
                }
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
            title: 'D4S',
            collapsable: false,
            sidebarDepth: 2,
            children: [
              '',
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
      }
    },
  }
}