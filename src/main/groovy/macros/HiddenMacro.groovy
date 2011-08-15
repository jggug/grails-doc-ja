package macros

import org.radeox.macro.BaseMacro
import org.radeox.macro.parameter.MacroParameter

class HiddenMacro extends BaseMacro {
    public HiddenMacro(){
    }
    String getName() { "hidden" }
    void execute(Writer out, MacroParameter params){
        out<<''
    }
}