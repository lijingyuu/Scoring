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

  function initImageLightbox() {
    var images = Array.prototype.slice.call(document.querySelectorAll('img[src]'))
    if (!images.length) return

    var overlay = document.createElement('div')
    overlay.className = 'image-lightbox'
    overlay.setAttribute('role', 'dialog')
    overlay.setAttribute('aria-modal', 'true')
    overlay.setAttribute('aria-label', '图片预览')

    var closeButton = document.createElement('button')
    closeButton.className = 'image-lightbox-close'
    closeButton.type = 'button'
    closeButton.setAttribute('aria-label', '关闭图片预览')
    closeButton.textContent = '×'

    var prevButton = document.createElement('button')
    prevButton.className = 'image-lightbox-nav image-lightbox-prev'
    prevButton.type = 'button'
    prevButton.setAttribute('aria-label', '查看上一张图片')
    prevButton.textContent = '‹'

    var nextButton = document.createElement('button')
    nextButton.className = 'image-lightbox-nav image-lightbox-next'
    nextButton.type = 'button'
    nextButton.setAttribute('aria-label', '查看下一张图片')
    nextButton.textContent = '›'

    var preview = document.createElement('img')
    preview.className = 'image-lightbox-img'
    preview.alt = ''

    var caption = document.createElement('div')
    caption.className = 'image-lightbox-caption'

    overlay.appendChild(closeButton)
    overlay.appendChild(prevButton)
    overlay.appendChild(preview)
    overlay.appendChild(nextButton)
    overlay.appendChild(caption)
    document.body.appendChild(overlay)

    var currentIndex = 0

    function updatePreview(index) {
      currentIndex = (index + images.length) % images.length

      var image = images[currentIndex]
      preview.src = image.currentSrc || image.src
      preview.alt = image.alt || ''
      caption.textContent = image.alt || ''
      caption.hidden = !image.alt
    }

    function closePreview() {
      overlay.classList.remove('open')
      document.body.classList.remove('image-lightbox-open')
      preview.removeAttribute('src')
    }

    function openPreview(image) {
      var index = images.indexOf(image)
      updatePreview(index === -1 ? 0 : index)
      overlay.classList.add('open')
      document.body.classList.add('image-lightbox-open')
      closeButton.focus()
    }

    function showPrevious() {
      updatePreview(currentIndex - 1)
    }

    function showNext() {
      updatePreview(currentIndex + 1)
    }

    images.forEach(function (image) {
      image.classList.add('is-previewable')
      image.tabIndex = 0
      image.setAttribute('role', 'button')
      image.setAttribute('aria-label', '查看大图：' + (image.alt || '产品图片'))

      image.addEventListener('click', function () {
        openPreview(image)
      })

      image.addEventListener('keydown', function (event) {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          openPreview(image)
        }
      })
    })

    closeButton.addEventListener('click', closePreview)
    prevButton.addEventListener('click', showPrevious)
    nextButton.addEventListener('click', showNext)
    overlay.addEventListener('click', function (event) {
      if (event.target === overlay) closePreview()
    })
    document.addEventListener('keydown', function (event) {
      if (!overlay.classList.contains('open')) return

      if (event.key === 'Escape') closePreview()
      if (event.key === 'ArrowLeft') showPrevious()
      if (event.key === 'ArrowRight') showNext()
    })
  }

  function boot() {
    var sidebarState = connectSidebarLinks()
    updateTopbarHeight()
    updateActiveSection(sidebarState)
    initImageLightbox()
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
