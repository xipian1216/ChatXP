from chatxp.core.config import ProviderModelConfig
from chatxp.schemas.catalog import ModelCapabilities, ModelListData, ModelOption


class ModelCatalog:
    def __init__(self, models: list[ProviderModelConfig], default_model: str) -> None:
        self._models = {item.id: item for item in models}
        self.default_model = default_model

    def contains(self, model_id: str) -> bool:
        return model_id in self._models

    def provider_model(self, model_id: str) -> str | None:
        item = self._models.get(model_id)
        return item.provider_model if item else None

    def public_models(self) -> ModelListData:
        return ModelListData(
            items=[
                ModelOption(
                    id=item.id,
                    display_name=item.display_name,
                    description=item.description,
                    is_default=item.id == self.default_model,
                    capabilities=ModelCapabilities(),
                )
                for item in self._models.values()
            ]
        )

