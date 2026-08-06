(function () {
  function connectSidebarLinks() {
    var links = Array.prototype.slice.call(document.querySelectorAll('.doc-nav a'))
    var sections = Array.prototype.slice.call(document.querySelectorAll('.doc-content > .section'))

    links.forEach(function (link, index) {
      var section = sections[index]
      if (!section) return

      if (!section.id) section.id = 'section-' + (index + 1)
      link.href = '#' + section.id
    })

    return { links: links, sections: sections }
  }

  function updateActiveSection(state) {
    if (!state || !state.sections.length) return

    var marker = window.innerHeight * 0.5
    var activeIndex = 0

    state.sections.forEach(function (section, index) {
      if (section.getBoundingClientRect().top <= marker) {
        activeIndex = index
      }
    })

    state.links.forEach(function (link, index) {
      link.classList.toggle('active', index === activeIndex)
    })
  }

  function updateTopbarHeight() {
    var topbar = document.querySelector('.topbar')
    if (!topbar) return

    document.documentElement.style.setProperty('--topbar-height', topbar.offsetHeight + 'px')
  }

  function boot() {
    var sidebarState = connectSidebarLinks()
    updateTopbarHeight()
    updateActiveSection(sidebarState)
    window.addEventListener('resize', updateTopbarHeight)
    window.addEventListener('resize', function () { updateActiveSection(sidebarState) })
    window.addEventListener('scroll', function () { updateActiveSection(sidebarState) }, { passive: true })
    window.addEventListener('load', function () {
      updateTopbarHeight()
      updateActiveSection(sidebarState)
    })
    setTimeout(updateTopbarHeight, 100)
    setTimeout(function () { updateActiveSection(sidebarState) }, 100)
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot)
  } else {
    boot()
  }
})()
