package io.github.minh124199.viettemplate.assets;

import io.github.minh124199.viettemplate.api.ContributorContext;
import io.github.minh124199.viettemplate.api.RenderContextContributor;
import io.github.minh124199.viettemplate.api.RenderRequest;
import java.util.Objects;

/**
 * {@link RenderContextContributor} that automatically registers {@code $assets} and {@code
 * $clientData} helpers in the template execution context.
 */
public final class FrontendAssetsRenderContextContributor implements RenderContextContributor {

  public static final String DEFAULT_ASSETS_VARIABLE_NAME = "assets";
  public static final String DEFAULT_CLIENT_DATA_VARIABLE_NAME = "clientData";

  private final FrontendAssets assets;
  private final ClientData clientData;
  private final String assetsVariableName;
  private final String clientDataVariableName;

  public FrontendAssetsRenderContextContributor(FrontendAssets assets, ClientData clientData) {
    this(assets, clientData, DEFAULT_ASSETS_VARIABLE_NAME, DEFAULT_CLIENT_DATA_VARIABLE_NAME);
  }

  public FrontendAssetsRenderContextContributor(
      FrontendAssets assets,
      ClientData clientData,
      String assetsVariableName,
      String clientDataVariableName) {
    this.assets = Objects.requireNonNull(assets, "assets must not be null");
    this.clientData = clientData;
    this.assetsVariableName =
        (assetsVariableName != null && !assetsVariableName.isBlank())
            ? assetsVariableName
            : DEFAULT_ASSETS_VARIABLE_NAME;
    this.clientDataVariableName =
        (clientDataVariableName != null && !clientDataVariableName.isBlank())
            ? clientDataVariableName
            : DEFAULT_CLIENT_DATA_VARIABLE_NAME;
  }

  @Override
  public void contribute(ContributorContext context, RenderRequest request) {
    context.put(this.assetsVariableName, this.assets);
    if (this.clientData != null) {
      context.put(this.clientDataVariableName, this.clientData);
    }
  }

  public FrontendAssets assets() {
    return assets;
  }

  public ClientData clientData() {
    return clientData;
  }
}
