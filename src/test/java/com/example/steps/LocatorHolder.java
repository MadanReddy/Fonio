package com.example.steps;

import org.openqa.selenium.By;

public class LocatorHolder {
    public final By primary;
    public final By fallback;

    public LocatorHolder(By primary, By fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }
}
