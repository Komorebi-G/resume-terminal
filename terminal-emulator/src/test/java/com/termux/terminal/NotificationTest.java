package com.termux.terminal;

/** [moke] OSC 9 / OSC 777 desktop notifications. */
public class NotificationTest extends TerminalTestCase {

    public void testOsc9Notification() {
        withTerminalSized(10, 3).enterString("\033]9;build done\007");
        assertEquals(1, mOutput.notifications.size());
        assertNull(mOutput.notifications.get(0)[0]);
        assertEquals("build done", mOutput.notifications.get(0)[1]);
    }

    public void testOsc9NumericSubcommandsAreNotNotifications() {
        // ConEmu extensions: "9;4;..." is a progress report (sent repeatedly), not something to notify about.
        withTerminalSized(10, 3).enterString("\033]9;4;1;50\007\033]9;4;0\033\\\033]9;1;100\007");
        assertEquals(0, mOutput.notifications.size());
    }

    public void testOsc777Notify() {
        withTerminalSized(10, 3).enterString("\033]777;notify;Title;Body; with semicolon\033\\");
        assertEquals(1, mOutput.notifications.size());
        assertEquals("Title", mOutput.notifications.get(0)[0]);
        assertEquals("Body; with semicolon", mOutput.notifications.get(0)[1]);
    }

    public void testOsc777OtherActionsIgnored() {
        withTerminalSized(10, 3).enterString("\033]777;preexec\007\033]777;foo;bar\007");
        assertEquals(0, mOutput.notifications.size());
    }

    public void testNotificationDoesNotPrint() {
        withTerminalSized(10, 3).enterString("\033]9;hi\007x").assertLinesAre("x         ", "          ", "          ");
    }
}
