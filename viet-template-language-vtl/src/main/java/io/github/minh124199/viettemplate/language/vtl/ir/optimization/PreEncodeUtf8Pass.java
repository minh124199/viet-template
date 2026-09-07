package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrTextConstant;
import java.util.List;

/**
 * Optimization pass ensuring that every static text constant in the template constant pool has a
 * pre-encoded UTF-8 byte array available for zero-copy streaming output.
 */
public final class PreEncodeUtf8Pass implements IrOptimizationPass {

  public static final String NAME = "PreEncodeUtf8";

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public IrTemplate run(IrTemplate template, OptimizationContext context) {
    if (!context.options().preEncodeUtf8()) {
      return template;
    }

    IrConstantPool pool = context.constantPool();
    List<IrTextConstant> constants = pool.allTextConstants();

    for (int i = 0; i < constants.size(); i++) {
      IrTextConstant tc = constants.get(i);
      if (tc != null && tc.utf8Bytes().isEmpty()) {
        IrTextConstant withUtf8 = IrTextConstant.of(tc.id(), tc.text(), tc.span());
        pool.add(withUtf8);
        context.statistics().recordUtf8ConstantEncoded();
      }
    }

    return template;
  }
}
