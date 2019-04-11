package com.example.plugins.tutorial.servlet;

public class CriticIssue {
    private int count;
    private int currentpage;
    private int totalpages;

    public int getCount() {
        return count;
    }
    public void setCount(int count) {
        this.count = count;
    }

    public int getCurrentpage() {
        return currentpage;
    }
    public void setCurrentpage(int currentpage) {
        this.currentpage = currentpage;
    }

    public int getTotalpages() {
        return totalpages;
    }
    public void setTotalpages(int totalpages) {
        this.totalpages = totalpages;
    }
}