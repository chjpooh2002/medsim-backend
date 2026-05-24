(function () {
  var inStart = window.location.pathname.includes('/start/');
  var root    = inStart ? '../' : '';
  var simHref = inStart ? 'start1.html' : 'start/start1.html';

  function updateNav() {
    var navRight = document.querySelector('.nav-right');
    if (!navRight) return;

    var token = localStorage.getItem('token');
    var name  = localStorage.getItem('name');

    if (token && name) {
      navRight.innerHTML =
        '<span style="font-size:13px;font-weight:600;color:var(--navy);">' + name + '님</span>' +
        '<a class="btn-outline" href="' + simHref + '">시뮬레이션</a>' +
        '<a class="btn-solid" href="#" onclick="logout();return false;">로그아웃</a>';
    } else {
      navRight.innerHTML =
        '<a class="btn-outline" href="' + root + 'signup.html">회원가입</a>' +
        '<a class="btn-solid" href="' + root + 'login.html">로그인</a>';
    }
  }

  function logout() {
    localStorage.clear();
    window.location.href = root + 'index.html';
  }

  function requireAuth() {
    if (!localStorage.getItem('token')) {
      window.location.replace(root + 'login.html');
    }
  }

  window.updateNav   = updateNav;
  window.logout      = logout;
  window.requireAuth = requireAuth;
})();
