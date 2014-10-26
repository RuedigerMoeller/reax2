document.write(
    '<template id="login-template"><span data-bind="spin: doSpin()">\
    <span data-bind="visible: !loggedIn()">\
        <input placeholder="user" size="6" type="text" data-bind="value: user">\
        <input placeholder="password" size="4" type="password" data-bind="value: pwd">\
        <button data-bind="click: login, enable: !doSpin()">Log In</button>\
    </span>\
    <span data-bind="visible: loggedIn()">Welcome <b data-bind="text: user"></b></span>\
    <b data-bind="hilight: true"><span data-bind="text: resultMsg" style="color: darkred;"></span></b>\
    </span></template>'
);