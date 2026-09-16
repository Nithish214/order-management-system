import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useApiFetch } from "../api/useApiFetch";
import { uploadProductImage } from "../api/productImages";
import { useCart } from "../cart/CartContext";
import { useAuth } from "../auth/AuthContext";
import { friendlyErrorMessage } from "../utils/errors";
import StockCount from "../components/StockCount";
import "./ProductDetailPage.css";

// Kept in sync with the same allowlist Order Service enforces server-side
// (ProductImageUploadService) -- identical constant to ProductsPage's.
const ACCEPTED_IMAGE_TYPES = "image/jpeg,image/png,image/webp";

export default function ProductDetailPage() {
  const { id } = useParams();
  const apiFetch = useApiFetch();
  const cart = useCart();
  const { isAdmin } = useAuth();
  const fileInputRef = useRef(null);

  const [product, setProduct] = useState(null);
  // Undefined until the /stock fetch resolves, distinct from null/0 -- StockCount treats
  // undefined the same as "don't know yet, say nothing", same reasoning as ProductsPage's
  // stockByProductId map simply not having an entry yet.
  const [stockQuantity, setStockQuantity] = useState(undefined);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [quantity, setQuantity] = useState(1);
  const [added, setAdded] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [restockInput, setRestockInput] = useState("");
  const [restocking, setRestocking] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function loadProduct() {
      try {
        // Two independent services -- Order Service owns the product itself, Inventory
        // Service owns its live stock. A stock lookup failure (e.g. no stock record for
        // some reason) shouldn't block showing the product, so it's handled separately,
        // not folded into the same try/catch as the product fetch.
        const response = await apiFetch(`/products/${id}`);
        if (!response.ok) {
          throw new Error(response.status === 404 ? "This product doesn't exist." : "Could not load this product");
        }
        const data = await response.json();
        if (!cancelled) setProduct(data);

        apiFetch(`/stock/${id}`)
          .then((stockResponse) => (stockResponse.ok ? stockResponse.json() : null))
          .then((stockData) => {
            if (!cancelled && stockData) setStockQuantity(stockData.availableQuantity);
          })
          .catch(() => {});
      } catch (err) {
        if (!cancelled) setError(friendlyErrorMessage(err));
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    loadProduct();
    return () => {
      cancelled = true;
    };
  }, [id, apiFetch]);

  function handleAddToCart() {
    cart.addItem(product, quantity);
    setAdded(true);
  }

  async function handleFileSelected(event) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;

    setUploading(true);
    setError(null);
    try {
      const updated = await uploadProductImage(apiFetch, product.id, file);
      setProduct((prev) => ({ ...prev, imageUrl: updated.imageUrl }));
    } catch (err) {
      setError(friendlyErrorMessage(err));
    } finally {
      setUploading(false);
    }
  }

  // Same "adds to whatever's already there" semantics as ProductsPage's restock control --
  // see StockController's comment on why there's no way to just overwrite a quantity.
  async function handleRestock() {
    const addQuantity = Number(restockInput);
    if (!Number.isInteger(addQuantity) || addQuantity <= 0) {
      setError("Enter a positive whole number to restock.");
      return;
    }

    setRestocking(true);
    setError(null);
    try {
      const response = await apiFetch(`/stock/${id}/restock`, {
        method: "POST",
        body: JSON.stringify({ quantity: addQuantity }),
      });
      if (!response.ok) {
        throw new Error("Could not restock this product");
      }
      const updated = await response.json();
      setStockQuantity(updated.availableQuantity);
      setRestockInput("");
    } catch (err) {
      setError(friendlyErrorMessage(err));
    } finally {
      setRestocking(false);
    }
  }

  if (loading) {
    return (
      <div className="detail-page">
        <p className="text-muted">Loading product...</p>
      </div>
    );
  }

  // A load failure with nothing to show at all gets the page to itself -- an error
  // sitting inline in a half-empty layout below would be more confusing, not less.
  if (error && !product) {
    return (
      <div className="detail-page">
        <p>
          <Link to="/" className="back-link">
            &larr; Back to products
          </Link>
        </p>
        <p className="text-error page-error">{error}</p>
      </div>
    );
  }

  return (
    <div className="detail-page">
      <p>
        <Link to="/" className="back-link">
          &larr; Back to products
        </Link>
      </p>

      <div className="detail-layout">
        <div className="detail-image">
          {product.imageUrl ? (
            <img src={product.imageUrl} alt={product.name} />
          ) : (
            <div className="detail-image-placeholder" aria-hidden="true" />
          )}
        </div>

        <div className="detail-info">
          <p className="detail-sku text-muted">{product.sku}</p>
          <h1 className="detail-name">{product.name}</h1>
          <p className="detail-price">${product.unitPrice.toFixed(2)}</p>
          <p className="detail-stock">
            <StockCount quantity={stockQuantity} />
          </p>

          {error && <p className="text-error page-error">{error}</p>}

          <div className="detail-quantity-row">
            <div className="quantity-stepper">
              <button
                type="button"
                onClick={() => setQuantity((q) => Math.max(1, q - 1))}
                aria-label="Decrease quantity"
              >
                &minus;
              </button>
              <input
                type="number"
                min="1"
                value={quantity}
                onChange={(e) => setQuantity(Math.max(1, Number(e.target.value)))}
                aria-label="Quantity"
              />
              <button type="button" onClick={() => setQuantity((q) => q + 1)} aria-label="Increase quantity">
                +
              </button>
            </div>
            <button className="btn-primary" onClick={handleAddToCart}>
              Add to cart
            </button>
          </div>

          {added && (
            <p className="text-muted">
              Added to cart. <Link to="/">View cart</Link>
            </p>
          )}

          {isAdmin && (
            <div className="detail-admin">
              <input
                type="file"
                accept={ACCEPTED_IMAGE_TYPES}
                ref={fileInputRef}
                onChange={handleFileSelected}
                hidden
              />
              <button
                type="button"
                className="btn-secondary"
                onClick={() => fileInputRef.current?.click()}
                disabled={uploading}
              >
                {uploading ? "Uploading..." : product.imageUrl ? "Change image" : "Upload image"}
              </button>
              <div className="restock-control">
                <input
                  type="number"
                  min="1"
                  placeholder="Qty"
                  value={restockInput}
                  onChange={(e) => setRestockInput(e.target.value)}
                  aria-label="Quantity to restock"
                />
                <button type="button" className="btn-secondary" onClick={handleRestock} disabled={restocking}>
                  {restocking ? "Restocking..." : "Restock"}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
