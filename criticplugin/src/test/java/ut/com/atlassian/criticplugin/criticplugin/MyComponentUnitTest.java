package ut.com.atlassian.criticplugin.criticplugin;

import org.junit.Test;
import com.atlassian.criticplugin.criticplugin.api.MyPluginComponent;
import com.atlassian.criticplugin.criticplugin.impl.MyPluginComponentImpl;

import static org.junit.Assert.assertEquals;

public class MyComponentUnitTest
{
    @Test
    public void testMyName()
    {
        MyPluginComponent component = new MyPluginComponentImpl(null);
        assertEquals("names do not match!", "myComponent",component.getName());
    }
}