import { useEffect, useRef, useState, startTransition } from 'react'
import './App.css'

const STORAGE_KEYS = {
  accessToken: 'ecom-access-token',
  refreshToken: 'ecom-refresh-token',
  userEmail: 'ecom-user-email',
}

const EMPTY_PRODUCT_FORM = { name: '', description: '', price: '', stock: '' }

function getStoredSession() {
  return {
    accessToken: localStorage.getItem(STORAGE_KEYS.accessToken) ?? '',
    refreshToken: localStorage.getItem(STORAGE_KEYS.refreshToken) ?? '',
    userEmail: localStorage.getItem(STORAGE_KEYS.userEmail) ?? '',
  }
}

function getTokenRole(token) {
  if (!token) return ''

  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const decodedPayload = atob(payload.padEnd(Math.ceil(payload.length / 4) * 4, '='))
    return JSON.parse(decodedPayload).role ?? ''
  } catch {
    return ''
  }
}

function persistSession(accessToken, refreshToken, userEmail) {
  localStorage.setItem(STORAGE_KEYS.accessToken, accessToken)
  localStorage.setItem(STORAGE_KEYS.refreshToken, refreshToken)
  localStorage.setItem(STORAGE_KEYS.userEmail, userEmail)
}

function clearSessionStorage() {
  localStorage.removeItem(STORAGE_KEYS.accessToken)
  localStorage.removeItem(STORAGE_KEYS.refreshToken)
  localStorage.removeItem(STORAGE_KEYS.userEmail)
}

function formatCurrency(value) {
  const amount = Number(value ?? 0)
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  }).format(amount)
}

function formatDate(value) {
  if (!value) return 'Unknown time'
  return new Intl.DateTimeFormat('en-IN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

function EmptyState({ title, body }) {
  return (
    <div className="empty-state">
      <h4>{title}</h4>
      <p>{body}</p>
    </div>
  )
}

function LoadingState({ label }) {
  return (
    <div className="empty-state">
      <h4>{label}</h4>
      <p>The frontend is syncing with your Spring backend.</p>
    </div>
  )
}

function App() {
  const [{ accessToken, refreshToken, userEmail }, setSession] = useState(getStoredSession)
  const [authMode, setAuthMode] = useState('login')
  const [authForm, setAuthForm] = useState({ name: '', email: '', password: '' })
  const [products, setProducts] = useState([])
  const [productPage, setProductPage] = useState({ page: 0, size: 5, totalPages: 0, totalElements: 0 })
  const [productForm, setProductForm] = useState(EMPTY_PRODUCT_FORM)
  const [editingProductId, setEditingProductId] = useState(null)
  const [isSavingProduct, setIsSavingProduct] = useState(false)
  const [deletingProductId, setDeletingProductId] = useState(null)
  const [cart, setCart] = useState(null)
  const [orders, setOrders] = useState([])
  const [isBootstrapping, setIsBootstrapping] = useState(false)
  const [isSubmittingAuth, setIsSubmittingAuth] = useState(false)
  const [isPlacingOrder, setIsPlacingOrder] = useState(false)
  const [activePanel, setActivePanel] = useState('products')
  const [statusMessage, setStatusMessage] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const refreshPromiseRef = useRef(null)

  const isLoggedIn = Boolean(accessToken)
  const isAdmin = getTokenRole(accessToken) === 'ADMIN'

  function extractErrorMessage(payload) {
    if (!payload) return 'Request failed'
    if (typeof payload === 'string') return payload
    if (payload.message) return payload.message
    if (payload.error) return payload.error
    return 'Request failed'
  }

  async function apiFetch(path, options = {}, retry = true) {
    const headers = new Headers(options.headers ?? {})
    const session = getStoredSession()

    if (options.body && !headers.has('Content-Type') && !(options.body instanceof FormData)) {
      headers.set('Content-Type', 'application/json')
    }

    if (session.accessToken) {
      headers.set('Authorization', `Bearer ${session.accessToken}`)
    }

    const response = await fetch(path, { ...options, headers })

    if (response.status === 401 && retry && session.refreshToken && !path.startsWith('/auth/refresh')) {
      const nextAccessToken = await refreshAccessToken()
      if (nextAccessToken) {
        return apiFetch(path, options, false)
      }
    }

    if (response.status === 204) return null

    const contentType = response.headers.get('content-type') ?? ''
    const payload = contentType.includes('application/json') ? await response.json() : await response.text()

    if (!response.ok) throw new Error(extractErrorMessage(payload))

    return payload
  }

  async function refreshAccessToken() {
    if (refreshPromiseRef.current) return refreshPromiseRef.current

    const currentRefreshToken = getStoredSession().refreshToken
    if (!currentRefreshToken) {
      forceLogout()
      return null
    }

    refreshPromiseRef.current = fetch('/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: currentRefreshToken }),
    })
      .then(async (response) => {
        const payload = await response.json()
        if (!response.ok) throw new Error(extractErrorMessage(payload))
        persistSession(payload.accessToken, currentRefreshToken, getStoredSession().userEmail)
        setSession(getStoredSession())
        return payload.accessToken
      })
      .catch(() => {
        forceLogout()
        return null
      })
      .finally(() => {
        refreshPromiseRef.current = null
      })

    return refreshPromiseRef.current
  }

  function resetFeedback() {
    setStatusMessage('')
    setErrorMessage('')
  }

  function forceLogout() {
    clearSessionStorage()
    setSession({ accessToken: '', refreshToken: '', userEmail: '' })
    setCart(null)
    setOrders([])
    setProducts([])
    setStatusMessage('Session ended. Please log in again.')
    setActivePanel('products')
  }

  async function reloadProducts() {
    const payload = await apiFetch(`/products?page=${productPage.page}&size=${productPage.size}`)
    setProducts(payload.content ?? [])
    setProductPage((current) => ({
      ...current,
      totalPages: payload.totalPages ?? 0,
      totalElements: payload.totalElements ?? 0,
    }))
  }

  async function reloadCart() {
    const payload = await apiFetch('/cart')
    setCart(payload)
  }

  async function reloadOrders() {
    const payload = await apiFetch('/orders')
    setOrders(payload ?? [])
  }

  useEffect(() => {
    if (!isLoggedIn) {
      setProducts([])
      setCart(null)
      setOrders([])
      return
    }

    let cancelled = false

    async function bootstrap() {
      setIsBootstrapping(true)
      setErrorMessage('')
      try {
        const [productData, cartData, orderData] = await Promise.all([
          apiFetch(`/products?page=${productPage.page}&size=${productPage.size}`),
          apiFetch('/cart'),
          apiFetch('/orders'),
        ])

        if (cancelled) return

        startTransition(() => {
          setProducts(productData.content ?? [])
          setProductPage((current) => ({
            ...current,
            totalPages: productData.totalPages ?? 0,
            totalElements: productData.totalElements ?? 0,
          }))
          setCart(cartData)
          setOrders(orderData ?? [])
        })
      } catch (error) {
        if (!cancelled) setErrorMessage(error.message)
      } finally {
        if (!cancelled) setIsBootstrapping(false)
      }
    }

    bootstrap()
    return () => {
      cancelled = true
    }
  }, [isLoggedIn, productPage.page, productPage.size])

  async function handleAuthSubmit(event) {
    event.preventDefault()
    resetFeedback()
    setIsSubmittingAuth(true)
    try {
      if (authMode === 'register') {
        await apiFetch('/users/register', {
          method: 'POST',
          body: JSON.stringify(authForm),
        })
        setStatusMessage('Registration successful. You can log in now.')
        setAuthMode('login')
      } else {
        const payload = await apiFetch('/users/login', {
          method: 'POST',
          body: JSON.stringify({ email: authForm.email, password: authForm.password }),
        })
        persistSession(payload.accessToken, payload.refreshToken, authForm.email)
        setSession(getStoredSession())
        setStatusMessage('Welcome back. Your storefront is ready.')
      }
      setAuthForm((current) => ({ ...current, password: '' }))
    } catch (error) {
      setErrorMessage(error.message)
    } finally {
      setIsSubmittingAuth(false)
    }
  }

  async function handleLogout() {
    resetFeedback()
    try {
      if (refreshToken) {
        await apiFetch('/auth/logout', {
          method: 'POST',
          body: JSON.stringify({ refreshToken }),
        })
      }
    } catch (error) {
      setErrorMessage(error.message)
    } finally {
      forceLogout()
    }
  }

  async function handleAddToCart(productId) {
    resetFeedback()
    try {
      const updatedCart = await apiFetch(`/cart/add?productId=${productId}&quantity=1`, { method: 'POST' })
      setCart(updatedCart)
      setStatusMessage('Added to cart.')
      setActivePanel('cart')
    } catch (error) {
      setErrorMessage(error.message)
    }
  }

  function handleEditProduct(product) {
    resetFeedback()
    setEditingProductId(product.id)
    setProductForm({
      name: product.name,
      description: product.description ?? '',
      price: String(product.price),
      stock: String(product.stock),
    })
    setActivePanel('admin')
  }

  function handleCancelProductEdit() {
    setEditingProductId(null)
    setProductForm(EMPTY_PRODUCT_FORM)
  }

  async function handleProductSubmit(event) {
    event.preventDefault()
    resetFeedback()

    const productRequest = {
      name: productForm.name.trim(),
      description: productForm.description.trim(),
      price: Number(productForm.price),
      stock: Number(productForm.stock),
    }

    if (!productRequest.name || !Number.isFinite(productRequest.price) || productRequest.price <= 0 || !Number.isInteger(productRequest.stock) || productRequest.stock < 0) {
      setErrorMessage('Enter a name, a price greater than zero, and a whole-number stock of zero or more.')
      return
    }

    setIsSavingProduct(true)
    try {
      const isEditing = Boolean(editingProductId)
      await apiFetch(isEditing ? `/products/${editingProductId}` : '/products', {
        method: isEditing ? 'PUT' : 'POST',
        body: JSON.stringify(productRequest),
      })
      handleCancelProductEdit()
      await reloadProducts()
      setStatusMessage(isEditing ? 'Product updated.' : 'Product added.')
    } catch (error) {
      setErrorMessage(error.message)
    } finally {
      setIsSavingProduct(false)
    }
  }

  async function handleDeleteProduct(product) {
    if (!window.confirm(`Delete ${product.name}? This cannot be undone.`)) return

    resetFeedback()
    setDeletingProductId(product.id)
    try {
      await apiFetch(`/products/${product.id}`, { method: 'DELETE' })
      if (editingProductId === product.id) handleCancelProductEdit()

      if (products.length === 1 && productPage.page > 0) {
        setProductPage((current) => ({ ...current, page: current.page - 1 }))
      } else {
        await reloadProducts()
      }
      setStatusMessage('Product deleted.')
    } catch (error) {
      setErrorMessage(error.message)
    } finally {
      setDeletingProductId(null)
    }
  }

  async function handleCartQuantity(productId, quantity) {
    resetFeedback()
    try {
      const updatedCart = await apiFetch('/cart/update', {
        method: 'PUT',
        body: JSON.stringify({ productId, quantity }),
      })
      setCart(updatedCart)
      setStatusMessage('Cart updated.')
    } catch (error) {
      setErrorMessage(error.message)
    }
  }

  async function handleRemoveFromCart(productId) {
    resetFeedback()
    try {
      const updatedCart = await apiFetch(`/cart/remove?productId=${productId}`, { method: 'DELETE' })
      setCart(updatedCart)
      setStatusMessage('Item removed from cart.')
    } catch (error) {
      setErrorMessage(error.message)
    }
  }

  async function handlePlaceOrder() {
    resetFeedback()
    setIsPlacingOrder(true)
    try {
      const order = await apiFetch('/orders/place', { method: 'POST' })
      setOrders((current) => [order, ...current])
      await Promise.all([reloadCart(), reloadProducts()])
      setStatusMessage('Order placed successfully.')
      setActivePanel('orders')
      reloadProducts();
    } catch (error) {
      setErrorMessage(error.message)
    } finally {
      setIsPlacingOrder(false)
    }
  }

  const cartItemCount = cart?.items?.reduce((sum, item) => sum + item.quantity, 0) ?? 0
  const panels = isAdmin ? ['products', 'cart', 'orders', 'admin'] : ['products', 'cart', 'orders']
  const panelLabels = { products: 'Products', cart: 'Cart', orders: 'Orders', admin: 'Admin' }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand-block">
          <span className="eyebrow">Sai Kiran storefront</span>
          <h1>Canvas Cart</h1>
          <p>Your ultimate shopping goal</p>
        </div>
        <div className="auth-card">
          <div className="auth-tabs">
            <button className={authMode === 'login' ? 'active' : ''} type="button" onClick={() => setAuthMode('login')}>Log In</button>
            <button className={authMode === 'register' ? 'active' : ''} type="button" onClick={() => setAuthMode('register')}>Register</button>
          </div>
          <form className="auth-form" onSubmit={handleAuthSubmit}>
            {authMode === 'register' ? (
              <label>
                Full Name
                <input value={authForm.name} onChange={(event) => setAuthForm((current) => ({ ...current, name: event.target.value }))} placeholder="Enter your full name" />
              </label>
            ) : null}
            <label>
              Email
              <input type="email" value={authForm.email} onChange={(event) => setAuthForm((current) => ({ ...current, email: event.target.value }))} placeholder="shopper@example.com" required />
            </label>
            <label>
              Password
              <input type="password" value={authForm.password} onChange={(event) => setAuthForm((current) => ({ ...current, password: event.target.value }))} placeholder="Enter password" required />
            </label>
            <button className="primary-button" type="submit" disabled={isSubmittingAuth}>
              {isSubmittingAuth ? 'Please wait...' : authMode === 'register' ? 'Create account' : 'Enter storefront'}
            </button>
          </form>
          <div className="session-state">
            <span className={`status-dot ${isLoggedIn ? 'live' : 'idle'}`}></span>
            {isLoggedIn ? `Signed in as ${userEmail}` : 'No active session'}
          </div>
          {isLoggedIn ? <button className="ghost-button" type="button" onClick={handleLogout}>Log out</button> : null}
        </div>
        {/* <div className="note-card">
          <h2>Backend contract</h2>
          <p>This frontend uses your Spring endpoints directly and refreshes the access token automatically on 401 responses.</p>
        </div> */}
      </aside>

      <main className="main-panel">
        <header className="hero-panel">
          <div>
            <span className="eyebrow">E-commerce control room</span>
            <h2>Browse, add, order, repeat.</h2>
            <p>Products and cart stay in sync with your backend. Orders appear instantly after checkout.</p>
          </div>
          <div className="hero-stats">
            <article><strong>{productPage.totalElements}</strong><span>Products</span></article>
            <article><strong>{cartItemCount}</strong><span>Cart items</span></article>
            <article><strong>{orders.length}</strong><span>Orders</span></article>
          </div>
        </header>

        <nav className="panel-tabs">
          {panels.map((panel) => (
            <button key={panel} type="button" className={activePanel === panel ? 'active' : ''} onClick={() => setActivePanel(panel)}>
              {panelLabels[panel]}
            </button>
          ))}
        </nav>

        {statusMessage ? <div className="banner success">{statusMessage}</div> : null}
        {errorMessage ? <div className="banner error">{errorMessage}</div> : null}

        <section className="content-grid">
          <div className={`panel ${activePanel === 'products' ? 'visible' : 'hidden'}`}>
            <div className="panel-head">
              <div><h3>Products</h3><p>Login first, then browse what products are available.</p></div>
              <button className="ghost-button" type="button" onClick={() => { resetFeedback(); reloadProducts().catch((error) => setErrorMessage(error.message)) }} disabled={!isLoggedIn || isBootstrapping}>Refresh list</button>
            </div>

            {!isLoggedIn ? (
              <EmptyState title="Authenticate to unlock products" body="Your backend protects product reads, so login or register in the left panel first." />
            ) : isBootstrapping ? (
              <LoadingState label="Loading products..." />
            ) : (
              <>
                <div className="product-grid">
                  {products.map((product) => (
                    <article className="product-card" key={product.id}>
                      <div className="product-badge">Stock {product.stock}</div>
                      <h4>{product.name}</h4>
                      <p>{product.description || 'No description provided.'}</p>
                      <div className="product-meta">
                        <strong>{formatCurrency(product.price)}</strong>
                        <span>{formatDate(product.createdAt)}</span>
                      </div>
                      <button className="primary-button" type="button" onClick={() => handleAddToCart(product.id)} disabled={product.stock <= 0}>
                        {product.stock <= 0 ? 'Out of stock' : 'Add to cart'}
                      </button>
                    </article>
                  ))}
                </div>
                <div className="pager">
                  <button className="ghost-button" type="button" disabled={productPage.page === 0} onClick={() => setProductPage((current) => ({ ...current, page: Math.max(0, current.page - 1) }))}>Previous</button>
                  <span>Page {productPage.page + 1} of {Math.max(productPage.totalPages, 1)}</span>
                  <button className="ghost-button" type="button" disabled={productPage.page + 1 >= productPage.totalPages} onClick={() => setProductPage((current) => ({ ...current, page: current.page + 1 }))}>Next</button>
                </div>
              </>
            )}
          </div>

          {isAdmin ? (
            <div className={`panel ${activePanel === 'admin' ? 'visible' : 'hidden'}`}>
              <div className="panel-head">
                <div><h3>Product administration</h3><p>Create, edit, and remove products from the catalog.</p></div>
                <span className="admin-count">{productPage.totalElements} products</span>
              </div>

              <form className="product-form" onSubmit={handleProductSubmit}>
                <div className="product-form-heading">
                  <h4>{editingProductId ? 'Edit product' : 'Add a product'}</h4>
                  {editingProductId ? <button className="ghost-button compact" type="button" onClick={handleCancelProductEdit}>Cancel edit</button> : null}
                </div>
                <div className="product-form-grid">
                  <label className="product-name-field">
                    Product name
                    <input maxLength="255" required value={productForm.name} onChange={(event) => setProductForm((current) => ({ ...current, name: event.target.value }))} />
                  </label>
                  <label className="product-description-field">
                    Description
                    <textarea rows="3" value={productForm.description} onChange={(event) => setProductForm((current) => ({ ...current, description: event.target.value }))} />
                  </label>
                  <label>
                    Price
                    <input type="number" min="0.01" step="0.01" required value={productForm.price} onChange={(event) => setProductForm((current) => ({ ...current, price: event.target.value }))} />
                  </label>
                  <label>
                    Stock
                    <input type="number" min="0" step="1" required value={productForm.stock} onChange={(event) => setProductForm((current) => ({ ...current, stock: event.target.value }))} />
                  </label>
                </div>
                <button className="primary-button" type="submit" disabled={isSavingProduct}>
                  {isSavingProduct ? 'Saving...' : editingProductId ? 'Save changes' : 'Add product'}
                </button>
              </form>

              <div className="admin-catalog">
                <div className="product-form-heading">
                  <h4>Catalog</h4>
                  <button className="ghost-button compact" type="button" onClick={() => reloadProducts().catch((error) => setErrorMessage(error.message))} disabled={isBootstrapping}>Refresh</button>
                </div>
                {!products.length ? (
                  <EmptyState title="No products on this page" body="Add a product above or change catalog pages." />
                ) : (
                  <div className="admin-table-wrap">
                    <table className="admin-table">
                      <thead>
                        <tr><th>Product</th><th>Price</th><th>Stock</th><th>Actions</th></tr>
                      </thead>
                      <tbody>
                        {products.map((product) => (
                          <tr key={product.id}>
                            <td><strong>{product.name}</strong><span>{product.description || 'No description'}</span></td>
                            <td>{formatCurrency(product.price)}</td>
                            <td>{product.stock}</td>
                            <td>
                              <div className="admin-actions">
                                <button className="ghost-button compact" type="button" onClick={() => handleEditProduct(product)}>Edit</button>
                                <button className="danger-button" type="button" onClick={() => handleDeleteProduct(product)} disabled={deletingProductId === product.id}>
                                  {deletingProductId === product.id ? 'Deleting...' : 'Delete'}
                                </button>
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
                <div className="pager">
                  <button className="ghost-button" type="button" disabled={productPage.page === 0} onClick={() => setProductPage((current) => ({ ...current, page: Math.max(0, current.page - 1) }))}>Previous</button>
                  <span>Page {productPage.page + 1} of {Math.max(productPage.totalPages, 1)}</span>
                  <button className="ghost-button" type="button" disabled={productPage.page + 1 >= productPage.totalPages} onClick={() => setProductPage((current) => ({ ...current, page: current.page + 1 }))}>Next</button>
                </div>
              </div>
            </div>
          ) : null}

          <div className={`panel ${activePanel === 'cart' ? 'visible' : 'hidden'}`}>
            <div className="panel-head">
              <div><h3>Cart</h3><p>Adjust quantities and place an order when the basket looks right.</p></div>
              <button className="ghost-button" type="button" onClick={() => { resetFeedback(); reloadCart().catch((error) => setErrorMessage(error.message)) }} disabled={!isLoggedIn}>Refresh cart</button>
            </div>

            {!isLoggedIn ? (
              <EmptyState title="Cart becomes available after login" body="Once authenticated, your cart will load from the backend and stay in sync." />
            ) : !cart?.items?.length ? (
              <EmptyState title="Your cart is empty" body="Browse products, add something you like, then come back here to update or order." />
            ) : (
              <>
                <div className="cart-list">
                  {cart.items.map((item) => (
                    <article className="cart-row" key={item.productId}>
                      <div>
                        <h4>{item.productName}</h4>
                        <span>{formatCurrency(item.price)} each</span>
                      </div>
                      <div className="cart-actions">
                        <button type="button" className="mini-button" onClick={() => handleCartQuantity(item.productId, item.quantity - 1)} disabled={item.quantity <= 1}>-</button>
                        <strong>{item.quantity}</strong>
                        <button type="button" className="mini-button" onClick={() => handleCartQuantity(item.productId, item.quantity + 1)}>+</button>
                        <button type="button" className="ghost-button compact" onClick={() => handleRemoveFromCart(item.productId)}>Remove</button>
                      </div>
                      <strong>{formatCurrency(item.totalPrice)}</strong>
                    </article>
                  ))}
                </div>
                <div className="checkout-bar">
                  <div><span>Total</span><strong>{formatCurrency(cart.totalPrice)}</strong></div>
                  <button className="primary-button" type="button" onClick={handlePlaceOrder} disabled={isPlacingOrder}>
                    {isPlacingOrder ? 'Placing order...' : 'Place order'}
                    
                  </button>
                </div>
              </>
            )}
          </div>

          <div className={`panel ${activePanel === 'orders' ? 'visible' : 'hidden'}`}>
            <div className="panel-head">
              <div><h3>Orders</h3><p>See the latest orders being created.</p></div>
              <button className="ghost-button" type="button" onClick={() => { resetFeedback(); reloadOrders().catch((error) => setErrorMessage(error.message)) }} disabled={!isLoggedIn}>Refresh orders</button>
            </div>

            {!isLoggedIn ? (
              <EmptyState title="Orders need an active user session" body="Login, place an order, and this view will show your order history." />
            ) : !orders.length ? (
              <EmptyState title="No orders yet" body="Your first checkout will appear here with total, items, and order timestamp." />
            ) : (
              <div className="orders-stack">
                {orders.map((order) => (
                  <article className="order-card" key={order.orderId}>
                    <div className="order-head">
                      <div><h4>Order #{order.orderId.slice(0, 8)}</h4><span>{formatDate(order.createdAt)}</span></div>
                      <div className="order-status">{order.status}</div>
                    </div>
                    <div className="order-items">
                      {order.items.map((item) => (
                        <div className="order-item" key={`${order.orderId}-${item.productId}`}>
                          <span>{item.productName}</span>
                          <span>{item.quantity} x {formatCurrency(item.price)}</span>
                        </div>
                      ))}
                    </div>
                    <div className="order-total"><span>Total amount</span><strong>{formatCurrency(order.totalAmount)}</strong></div>
                  </article>
                ))}
              </div>
            )}
          </div>
        </section>
      </main>
    </div>
  )
}

export default App
