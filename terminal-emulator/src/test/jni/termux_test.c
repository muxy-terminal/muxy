#include <assert.h>
#include <errno.h>
#include <jni.h>
#include <signal.h>
#include <string.h>
#include <sys/wait.h>

static pid_t interrupted_waitpid(pid_t pid, int* status, int options);
#define waitpid interrupted_waitpid
#include "../../main/jni/termux.c"
#undef waitpid

static int wait_calls;
static int interruptions;
static int wait_error;
static int child_status;
static char exception_message[256];

static pid_t interrupted_waitpid(pid_t pid, int* status, int options)
{
    assert(pid == 42);
    assert(options == 0);
    ++wait_calls;
    if (interruptions > 0) {
        --interruptions;
        errno = EINTR;
        return -1;
    }
    if (wait_error != 0) {
        errno = wait_error;
        return -1;
    }
    *status = child_status;
    return pid;
}

static jclass find_class(JNIEnv* TERMUX_UNUSED(env), const char* name)
{
    assert(strcmp(name, "java/lang/RuntimeException") == 0);
    return (jclass) &exception_message;
}

static jint throw_new(JNIEnv* TERMUX_UNUSED(env), jclass TERMUX_UNUSED(clazz), const char* message)
{
    snprintf(exception_message, sizeof(exception_message), "%s", message);
    return JNI_OK;
}

static void reset_wait(void)
{
    wait_calls = 0;
    interruptions = 0;
    wait_error = 0;
    child_status = 0;
    exception_message[0] = '\0';
}

static void test_wait_retries_interrupted_calls(JNIEnv* env)
{
    reset_wait();
    interruptions = 3;
    child_status = 37 << 8;
    assert(Java_com_termux_terminal_JNI_waitFor(env, NULL, 42) == 37);
    assert(wait_calls == 4);
    assert(exception_message[0] == '\0');

    reset_wait();
    interruptions = 2;
    child_status = SIGTERM;
    assert(Java_com_termux_terminal_JNI_waitFor(env, NULL, 42) == -SIGTERM);
    assert(wait_calls == 3);
    assert(exception_message[0] == '\0');
}

static void test_wait_errors_do_not_become_exit_statuses(JNIEnv* env)
{
    reset_wait();
    interruptions = 1;
    wait_error = ECHILD;
    assert(Java_com_termux_terminal_JNI_waitFor(env, NULL, 42) == -1);
    assert(wait_calls == 2);
    assert(strstr(exception_message, "Cannot wait for subprocess:") != NULL);

    reset_wait();
    child_status = (SIGSTOP << 8) | 0x7f;
    assert(Java_com_termux_terminal_JNI_waitFor(env, NULL, 42) == -1);
    assert(strcmp(exception_message, "Unexpected subprocess wait status") == 0);

    reset_wait();
    assert(Java_com_termux_terminal_JNI_waitFor(env, NULL, 0) == -1);
    assert(wait_calls == 0);
    assert(strcmp(exception_message, "Invalid subprocess PID") == 0);
}

static void test_subprocess_has_a_controlling_terminal_and_output(JNIEnv* env)
{
    char* argv[] = {"/bin/sh", "-c", "test -t 0 && test -t 1 && test -t 2 && printf 'pty-ready'", NULL};
    char* envp[] = {"PATH=/usr/bin:/bin", NULL};
    int pid = 0;
    reset_wait();
    int master = create_subprocess(env, argv[0], "/", argv, envp, &pid, 24, 80, 8, 16);
    assert(master >= 0);
    assert(pid > 0);
    assert(exception_message[0] == '\0');
    assert((fcntl(master, F_GETFD) & FD_CLOEXEC) != 0);

    struct termios attributes;
    assert(tcgetattr(master, &attributes) == 0);
    assert((attributes.c_iflag & IUTF8) != 0);
    assert((attributes.c_iflag & (IXON | IXOFF)) == 0);
    struct winsize size;
    assert(ioctl(master, TIOCGWINSZ, &size) == 0);
    assert(size.ws_row == 24 && size.ws_col == 80);
    assert(size.ws_xpixel == 640 && size.ws_ypixel == 384);

    char output[32] = {0};
    size_t used = 0;
    while (used < sizeof(output) - 1) {
        ssize_t count = read(master, output + used, sizeof(output) - 1 - used);
        if (count < 0 && errno == EINTR) continue;
        if (count < 0 && errno == EIO) break;
        assert(count >= 0);
        if (count == 0) break;
        used += (size_t) count;
    }
    assert(strcmp(output, "pty-ready") == 0);
    int status;
    pid_t result;
    do {
        result = waitpid(pid, &status, 0);
    } while (result < 0 && errno == EINTR);
    assert(result == pid);
    assert(WIFEXITED(status) && WEXITSTATUS(status) == 0);
    assert(close(master) == 0);
}

int main(void)
{
    const struct JNINativeInterface_ functions = {
        .FindClass = find_class,
        .ThrowNew = throw_new
    };
    JNIEnv env = &functions;
    test_wait_retries_interrupted_calls(&env);
    test_wait_errors_do_not_become_exit_statuses(&env);
    test_subprocess_has_a_controlling_terminal_and_output(&env);
    puts("PTY subprocess and interrupted-wait tests passed");
    return 0;
}
