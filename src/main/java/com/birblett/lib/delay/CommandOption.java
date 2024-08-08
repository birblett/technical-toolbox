package com.birblett.lib.delay;

public interface CommandOption {

    void setOpt(String s, Object value);
    Object getOpt(String s);
    void resetOpt();

}
