package com.birblett.lib.command.delay;

public interface CommandOption {

    void setOpt(String s, Object value);
    Object getOpt(String s);
    void resetOpt();

}
