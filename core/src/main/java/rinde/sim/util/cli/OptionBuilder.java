package rinde.sim.util.cli;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.PatternOptionBuilder;

import com.google.common.base.Joiner;

public class OptionBuilder {
  private static final int NUM_ARGS_IN_LIST = 100;
  private static final String ARG_LIST_NAME = "list";
  private static final char ARG_LIST_SEPARATOR = ',';

  private static final String NUM_NAME = "num";
  private static final String STRING_NAME = "string";
  private final Option option;

  OptionBuilder(MenuOption im) {
    this(im.getShortName(), im.getLongName());
  }

  OptionBuilder(String sn, String ln) {
    option = new Option(sn, "");
    option.setLongOpt(ln);
  }

  public OptionBuilder numberArgList() {
    option.setArgs(NUM_ARGS_IN_LIST);
    option.setArgName(ARG_LIST_NAME);
    option.setType(PatternOptionBuilder.NUMBER_VALUE);
    option.setValueSeparator(ARG_LIST_SEPARATOR);
    return this;
  }

  public OptionBuilder numberArg() {
    option.setArgs(1);
    option.setArgName(NUM_NAME);
    option.setType(PatternOptionBuilder.NUMBER_VALUE);
    return this;
  }

  public OptionBuilder stringArgList() {
    option.setArgs(NUM_ARGS_IN_LIST);
    option.setArgName(ARG_LIST_NAME);
    option.setType(PatternOptionBuilder.STRING_VALUE);
    option.setValueSeparator(ARG_LIST_SEPARATOR);
    return this;
  }

  public OptionBuilder stringArg() {
    option.setArgs(1);
    option.setArgName(STRING_NAME);
    option.setType(PatternOptionBuilder.NUMBER_VALUE);
    return this;
  }

  public OptionBuilder optionalArg() {
    option.setOptionalArg(true);
    return this;
  }

  public OptionBuilder description(Object... desc) {
    option.setDescription(Joiner.on("").join(desc));
    return this;
  }

  public Option build() {
    return option;
  }

  public static OptionBuilder optionBuilder(MenuOption im) {
    return new OptionBuilder(im);
  }

  /**
   * Sets all variables from the specified {@link Option} except the short and
   * long name.
   * @param opt
   * @return
   */
  public OptionBuilder set(Option opt) {
    option.setArgs(opt.getArgs());
    option.setArgName(opt.getArgName());
    option.setDescription(opt.getDescription());
    option.setOptionalArg(opt.hasOptionalArg());
    option.setType(opt.getType());
    option.setValueSeparator(opt.getValueSeparator());
    option.setRequired(opt.isRequired());
    return this;
  }
}
