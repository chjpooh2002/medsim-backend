(function () {

  function updateNav() {
    var navRight = document.querySelector('.nav-right');
    if (!navRight) return;

    var token = localStorage.getItem('token');
    var name  = localStorage.getItem('name');

    if (token && name) {
      navRight.innerHTML =
        '<span style="font-size:13px;font-weight:600;color:var(--navy,#0D1B2A);">' + name + '님</span>' +
        '<a class="btn-outline" href="/start/start1.html">시뮬레이션</a>' +
        '<a class="btn-solid" href="#" onclick="logout();return false;">로그아웃</a>';
    } else {
      navRight.innerHTML =
        '<a class="btn-outline" href="/signup.html">회원가입</a>' +
        '<a class="btn-solid" href="/login.html">로그인</a>';
    }
  }

  function logout() {
    localStorage.clear();
    window.location.href = '/index.html';
  }

  function requireAuth() {
    if (!localStorage.getItem('token')) {
      window.location.replace('/login.html');
    }
  }

  window.updateNav   = updateNav;
  window.logout      = logout;
  window.requireAuth = requireAuth;

  // 스크립트 위치(head/body 하단)에 관계없이 DOM 준비 후 실행
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', updateNav);
  } else {
    updateNav();
  }

})();
