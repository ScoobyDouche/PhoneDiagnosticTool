// Contract for the privileged helper that Shizuku binds and runs as the shell
// user (UID 2000). The app process itself cannot read many sysfs nodes because
// SELinux confines untrusted_app; a command run through this interface executes
// in the shell domain, which most vendors allow to read them.
package com.phonediagnostic.data.elevated;

interface IElevatedService {
    // Shizuku calls this transaction on unbind to tear the helper process down;
    // the reserved id matches the Shizuku UserService convention.
    void destroy() = 16777114;

    // Runs `sh -c <command>` in the helper process and returns its stdout, or
    // null on any failure. Used for small sysfs reads, so no streaming is needed.
    String exec(String command) = 1;
}
